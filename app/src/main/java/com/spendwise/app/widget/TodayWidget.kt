package com.spendwise.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.spendwise.app.MainActivity
import com.spendwise.app.R
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.settingsFrom
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.expenses.expenseFrom
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.DaySpend
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.budgetState
import com.spendwise.app.domain.monthSpendDetail
import com.spendwise.app.domain.monthSpending
import com.spendwise.app.domain.daySpend
import com.spendwise.app.domain.daySpendDetail
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.format.formatMoney
import com.spendwise.app.ui.navigation.LINK_SCHEME
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Home-screen widget: today's spending, with + for Add expense. Android only; nothing new is stored. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
}

/** The 4x2 widget: this month against the budget, with today underneath. Filled by the same worker and sync. */
class MonthWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
}

/** A widget placed, or due its 30-minute refresh: the worker fills it now, and if the app is running it goes live. */
private fun refresh(context: Context) {
    TodayWidgetWorker.enqueue(context)
    EntryPointAccessors.fromApplication(context, TodayWidgetEntryPoint::class.java).sync().start()
}

/** What the widgets show: null spend means signed out. */
private data class TodayView(val spend: DaySpend?, val currency: Currency, val day: LocalDate, val monthSpent: Double = 0.0, val budget: Double? = null, val transparency: Int = 0)

private fun todayView(expenses: List<Expense>, budget: Double?, currency: Currency, day: LocalDate, transparency: Int): TodayView {
    val today = todayStr(day)
    return TodayView(daySpend(expenses, today), currency, day, monthSpending(expenses, today), budget, transparency)
}

private object TodayWidgetView {
    private val dayLabel = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val monthLabel = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)

    fun installed(context: Context) = ids(context, TodayWidget::class.java).isNotEmpty() || ids(context, MonthWidget::class.java).isNotEmpty()

    private fun ids(context: Context, provider: Class<*>) =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

    fun show(context: Context, view: TodayView) {
        showToday(context, view)
        showMonth(context, view)
    }

    private fun showMonth(context: Context, view: TodayView) {
        val rv = RemoteViews(context.packageName, R.layout.widget_month)
        val spend = view.spend
        if (spend == null) {
            rv.setTextViewText(R.id.month_label, "SpendWise")
            rv.setViewVisibility(R.id.month_amount, View.GONE)
            rv.setTextViewText(R.id.month_detail, "Sign in to see this month's spending")
            rv.setViewVisibility(R.id.month_add, View.GONE)
            rv.setViewVisibility(R.id.month_bar, View.GONE)
            rv.setViewVisibility(R.id.month_today, View.GONE)
        } else {
            val money = { n: Double -> formatMoney(n, view.currency) }
            val state = budgetState(view.monthSpent, view.budget)
            rv.setTextViewText(R.id.month_label, view.day.format(monthLabel))
            rv.setViewVisibility(R.id.month_amount, View.VISIBLE)
            rv.setTextViewText(R.id.month_amount, money(view.monthSpent))
            rv.setTextViewText(R.id.month_detail, monthSpendDetail(view.monthSpent, view.budget, money))
            rv.setViewVisibility(R.id.month_bar, if (state == null) View.GONE else View.VISIBLE)
            rv.setProgressBar(R.id.month_bar, 100, ((state?.ratio ?: 0.0) * 100).toInt().coerceIn(0, 100), false)
            rv.setViewVisibility(R.id.month_today, View.VISIBLE)
            rv.setTextViewText(R.id.month_today, "Today ${money(spend.total)} · ${daySpendDetail(spend.entries)}")
            rv.setViewVisibility(R.id.month_add, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.month_add, open(context, "add"))
        }
        rv.setInt(android.R.id.background, "setBackgroundResource", background(view.transparency))
        rv.setOnClickPendingIntent(android.R.id.background, open(context, "dashboard"))
        AppWidgetManager.getInstance(context).updateAppWidget(ComponentName(context, MonthWidget::class.java), rv)
    }

    private fun showToday(context: Context, view: TodayView) {
        val rv = RemoteViews(context.packageName, R.layout.widget_today)
        val spend = view.spend
        if (spend == null) {
            rv.setTextViewText(R.id.widget_label, "SpendWise")
            rv.setViewVisibility(R.id.widget_amount, View.GONE)
            rv.setTextViewText(R.id.widget_detail, "Sign in to see today's spending")
            rv.setViewVisibility(R.id.widget_add, View.GONE)
        } else {
            rv.setTextViewText(R.id.widget_label, "Today · ${view.day.format(dayLabel)}")
            rv.setViewVisibility(R.id.widget_amount, View.VISIBLE)
            rv.setTextViewText(R.id.widget_amount, formatMoney(spend.total, view.currency))
            rv.setTextViewText(R.id.widget_detail, daySpendDetail(spend.entries))
            rv.setViewVisibility(R.id.widget_add, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.widget_add, open(context, "add"))
        }
        rv.setInt(android.R.id.background, "setBackgroundResource", background(view.transparency))
        rv.setOnClickPendingIntent(android.R.id.background, open(context, "dashboard"))
        AppWidgetManager.getInstance(context).updateAppWidget(ComponentName(context, TodayWidget::class.java), rv)
    }

    /** The rounded surface at the chosen see-through level; the 100% one is the original drawable. */
    private fun background(transparency: Int) = when (transparency) {
        25 -> R.drawable.widget_background_75
        50 -> R.drawable.widget_background_50
        75 -> R.drawable.widget_background_25
        else -> R.drawable.widget_background
    }

    private fun open(context: Context, page: String) = PendingIntent.getActivity(
        context, page.hashCode(),
        Intent(Intent.ACTION_VIEW, "$LINK_SCHEME://$page".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    fun auth(): AuthRepository
    fun prefs(): DevicePrefs
    fun sync(): TodayWidgetSync
}

/** Reads the expenses when the app isn't running: the 30-minute refresh, and a widget just placed. Free once the cache has data. */
class TodayWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, TodayWidgetEntryPoint::class.java)
        val currency = deps.prefs().currency.first()
        val transparency = deps.prefs().widgetTransparency.first()
        val day = LocalDate.now()
        val uid = deps.auth().currentUser?.uid
            ?: return Result.success().also { TodayWidgetView.show(applicationContext, TodayView(null, currency, day, transparency = transparency)) }
        return try {
            val db = FirebaseFirestore.getInstance()
            val mine = db.collection("expenses").whereEqualTo("uid", uid)
            // The cache is kept current by the app's listeners, so a refresh needn't cost a server read every 30
            // minutes; only a cache the app never filled (just installed, data cleared) goes to the server, once,
            // which fills it. An entry made on the web shows once the app next opens (it then updates live).
            val source = if (mine.limit(1).get(Source.CACHE).await().isEmpty) Source.DEFAULT else Source.CACHE
            // The month needs more than today, and a uid + date range would need an index (spec §0.6): all of them, filtered here
            val expenses = mine.get(source).await().documents.map { expenseFrom(it.id, it.data.orEmpty()) }
            val budget = try {
                settingsFrom(db.collection("settings").document(uid).get(source).await().data).budget.total
            } catch (e: FirebaseFirestoreException) {
                null // not in the cache yet: the month shows without its budget until the app opens
            }
            TodayWidgetView.show(applicationContext, todayView(expenses, budget, currency, day, transparency))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) = WorkManager.getInstance(context)
            .enqueueUniqueWork("today-widget", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<TodayWidgetWorker>().build())
    }
}

/** While the app runs, the widget follows the live expenses, so an entry shows the moment it's saved. */
@Singleton
class TodayWidgetSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
    private val prefs: DevicePrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val following = AtomicBoolean(false)

    /** At app start, and whenever a widget updates; only the first call with a widget placed starts following. */
    fun start() {
        // The widget lookup is a call into the system: kept off the main thread so it never slows app start
        scope.launch { if (TodayWidgetView.installed(context) && following.compareAndSet(false, true)) follow() }
    }

    private fun follow() {
        combine(
            auth.authState.filter { it !is AuthState.Loading },
            expenses.expenses().catch { emit(emptyList()) },
            settings.settings().catch { emit(UserSettings()) },
            prefs.currency,
            prefs.widgetTransparency,
        ) { state, list, s, currency, transparency ->
            val day = LocalDate.now()
            if (state is AuthState.SignedIn) todayView(list, s.budget.total, currency, day, transparency) else TodayView(null, currency, day, transparency = transparency)
        }
            .distinctUntilChanged()
            .onEach { TodayWidgetView.show(context, it) }
            .launchIn(scope)
    }
}
