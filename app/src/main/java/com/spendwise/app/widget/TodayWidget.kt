package com.spendwise.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
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
import com.spendwise.app.SpendWiseActivity
import com.spendwise.app.R
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.settingsFrom
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.expenses.expenseFrom
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.BudgetLevel
import com.spendwise.app.domain.BudgetState
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.DaySpend
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.budgetState
import com.spendwise.app.domain.dayVsAverage
import com.spendwise.app.domain.isSpending
import com.spendwise.app.domain.monthSpendDetail
import com.spendwise.app.domain.monthSpending
import com.spendwise.app.domain.daySpend
import com.spendwise.app.domain.daySpendDetail
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatNumber
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
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

/** Home-screen widget: today's spending, with + for Add expense. Android only; nothing new is stored. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
}

/** The 4x2 widget: this month against the budget, with today underneath. Filled by the same worker and sync. */
class MonthWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
}

/** The 4x4 widget: the month, today's entries, and a quick add for a few categories. */
class LargeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
}

/** A widget placed, or due its 30-minute refresh: the worker fills it now, and if the app is running it goes live. */
private fun refresh(context: Context) {
    TodayWidgetWorker.enqueue(context)
    EntryPointAccessors.fromApplication(context, TodayWidgetEntryPoint::class.java).sync().start()
}

/** What the widgets show: null spend means signed out. [todays] is today's spending entries; [vsAverage] is today minus the daily average so far. */
private data class TodayView(
    val spend: DaySpend?,
    val currency: Currency,
    val day: LocalDate,
    val monthSpent: Double = 0.0,
    val budget: Double? = null,
    val transparency: Int = 0,
    val todays: List<Expense> = emptyList(),
    val vsAverage: Double? = null,
)

private fun todayView(expenses: List<Expense>, budget: Double?, currency: Currency, day: LocalDate, transparency: Int): TodayView {
    val today = todayStr(day)
    val spend = daySpend(expenses, today)
    val monthSpent = monthSpending(expenses, today)
    return TodayView(
        spend, currency, day, monthSpent, budget, transparency,
        todays = expenses.filter { it.date == today && isSpending(it.category) },
        vsAverage = if (spend.entries > 0) dayVsAverage(monthSpent, spend.total, day.dayOfMonth) else null,
    )
}

/** The Large widget's quick-add chips: label, and the category the Add sheet opens on. */
private val QUICK_ADD = listOf("Food" to "Food & Dining", "Groceries" to "Groceries", "Transport" to "Transport")

private object TodayWidgetView {
    private val dayLabel = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val monthLabel = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)

    fun installed(context: Context) = listOf(TodayWidget::class.java, MonthWidget::class.java, LargeWidget::class.java).any { ids(context, it).isNotEmpty() }

    private fun ids(context: Context, provider: Class<*>) =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

    fun show(context: Context, view: TodayView) {
        val manager = AppWidgetManager.getInstance(context)
        for (id in ids(context, TodayWidget::class.java)) {
            val rv = RemoteViews(context.packageName, R.layout.widget_today)
            val k = scale(manager, id, 110f, 110f)
            rv.size(R.id.widget_label, 11f, k); rv.size(R.id.widget_date, 12f, k); rv.size(R.id.widget_detail, 12f, k); rv.size(R.id.widget_vs, 12f, k)
            fillToday(context, rv, view)
            manager.updateAppWidget(id, rv)
        }
        for (id in ids(context, MonthWidget::class.java)) {
            val rv = RemoteViews(context.packageName, R.layout.widget_month)
            fillMonth(context, rv, view)
            fillMonthFoot(rv, view)
            manager.updateAppWidget(id, rv)
        }
        for (id in ids(context, LargeWidget::class.java)) {
            val rv = RemoteViews(context.packageName, R.layout.widget_large)
            fillMonth(context, rv, view)
            fillLarge(context, rv, view)
            manager.updateAppWidget(id, rv)
        }
    }

    /** Text grows and shrinks with the size the widget is dragged to, from its default size [baseW]x[baseH] dp. */
    private fun scale(manager: AppWidgetManager, id: Int, baseW: Float, baseH: Float): Float {
        val o = manager.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return if (w == 0 || h == 0) 1f else minOf(w / baseW, h / baseH).coerceIn(0.8f, 1.8f)
    }

    private fun RemoteViews.size(id: Int, sp: Float, k: Float) = setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * k)

    /** On Android 12+ the launcher resolves the colour, so it follows a theme switch without a redraw. */
    private fun RemoteViews.color(context: Context, id: Int, res: Int) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setColor(id, "setTextColor", res) else setTextColor(id, context.getColor(res))

    /** "₹5,320" as one line, the symbol smaller so the number leads. No colour span: it would miss a theme switch. */
    private fun money(context: Context, view: TodayView, n: Double): CharSequence {
        val symbol = view.currency.symbol
        return SpannableString(symbol + formatNumber(n, view.currency)).apply {
            setSpan(RelativeSizeSpan(0.62f), 0, symbol.length, 0)
        }
    }

    private fun fillToday(context: Context, rv: RemoteViews, view: TodayView) {
        val spend = view.spend
        if (spend == null) {
            rv.setTextViewText(R.id.widget_label, "SpendWise")
            rv.setViewVisibility(R.id.widget_date, View.GONE)
            rv.setViewVisibility(R.id.widget_amount, View.GONE)
            rv.setTextViewText(R.id.widget_detail, "Sign in to see today's spending")
            rv.setViewVisibility(R.id.widget_vs, View.GONE)
            rv.setViewVisibility(R.id.widget_add, View.GONE)
        } else {
            rv.setTextViewText(R.id.widget_label, "Today")
            rv.setViewVisibility(R.id.widget_date, View.VISIBLE)
            rv.setTextViewText(R.id.widget_date, view.day.format(dayLabel))
            rv.setViewVisibility(R.id.widget_amount, View.VISIBLE)
            rv.setTextViewText(R.id.widget_amount, money(context, view, spend.total))
            rv.setTextViewText(R.id.widget_detail, daySpendDetail(spend.entries))
            val gap = view.vsAverage
            rv.setViewVisibility(R.id.widget_vs, if (gap == null) View.GONE else View.VISIBLE)
            if (gap != null) {
                rv.setTextViewText(R.id.widget_vs, formatAmount(Math.round(abs(gap)).toDouble(), view.currency) + if (gap < 0) " under avg" else " over avg")
                rv.color(context, R.id.widget_vs, if (gap < 0) R.color.widget_positive else R.color.widget_text_muted)
            }
            rv.setViewVisibility(R.id.widget_add, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.widget_add, open(context, "add"))
        }
        rv.setInt(android.R.id.background, "setBackgroundResource", background(view.transparency))
        rv.setOnClickPendingIntent(android.R.id.background, open(context, "dashboard"))
    }

    /** The head shared by the 4x2 and 4x4 widgets. */
    private fun fillMonth(context: Context, rv: RemoteViews, view: TodayView) {
        val spend = view.spend
        if (spend == null) {
            rv.setTextViewText(R.id.month_label, "SpendWise")
            rv.setViewVisibility(R.id.month_amount, View.GONE)
            rv.setTextViewText(R.id.month_detail, "Sign in to see this month's spending")
            rv.setViewVisibility(R.id.month_add, View.GONE)
            rv.setViewVisibility(R.id.month_left, View.GONE)
            rv.setViewVisibility(R.id.month_bar, View.GONE)
            rv.setViewVisibility(R.id.month_bar_over, View.GONE)
            rv.setViewVisibility(R.id.month_status, View.GONE)
        } else {
            val state = budgetState(view.monthSpent, view.budget)
            rv.setTextViewText(R.id.month_label, view.day.format(monthLabel))
            rv.setViewVisibility(R.id.month_amount, View.VISIBLE)
            rv.setTextViewText(R.id.month_amount, money(context, view, view.monthSpent))
            fillBudgetLine(rv, view, state)
            val over = state?.level == BudgetLevel.OVER
            rv.setViewVisibility(R.id.month_bar, if (state == null || over) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.month_bar_over, if (over) View.VISIBLE else View.GONE)
            if (state != null) rv.setProgressBar(if (over) R.id.month_bar_over else R.id.month_bar, 1000, (state.ratio.coerceIn(0.0, 1.0) * 1000).toInt(), false)
            rv.setViewVisibility(R.id.month_status, if (state == null) View.GONE else View.VISIBLE)
            if (state != null) {
                rv.setTextViewText(R.id.month_status, when (state.level) { BudgetLevel.OK -> "On track"; BudgetLevel.NEAR -> "Near limit"; BudgetLevel.OVER -> "Over budget" })
                rv.color(context, R.id.month_status, when (state.level) { BudgetLevel.OK -> R.color.widget_positive; BudgetLevel.NEAR -> R.color.widget_text; BudgetLevel.OVER -> R.color.widget_negative })
            }
            rv.setViewVisibility(R.id.month_add, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.month_add, open(context, "add"))
        }
        rv.setInt(android.R.id.background, "setBackgroundResource", background(view.transparency))
        rv.setOnClickPendingIntent(android.R.id.background, open(context, "dashboard"))
    }

    /** "₹33,163 left of ₹68,900": the amount left in its own view, in the text colour, so both follow the theme. */
    private fun fillBudgetLine(rv: RemoteViews, view: TodayView, state: BudgetState?) {
        val money = { n: Double -> formatAmount(n, view.currency) }
        val line = monthSpendDetail(view.monthSpent, view.budget, money)
        val amount = state?.let { money(if (it.level == BudgetLevel.OVER) -it.left else it.left) }?.takeIf { line.startsWith(it) }
        rv.setViewVisibility(R.id.month_left, if (amount == null) View.GONE else View.VISIBLE)
        rv.setTextViewText(R.id.month_left, amount.orEmpty())
        rv.setTextViewText(R.id.month_detail, if (amount == null) line else line.drop(amount.length).trimStart())
    }

    private fun fillMonthFoot(rv: RemoteViews, view: TodayView) {
        val spend = view.spend
        rv.setViewVisibility(R.id.month_today, if (spend == null) View.GONE else View.VISIBLE)
        rv.setViewVisibility(R.id.month_perday, if (spend == null || view.monthSpent <= 0) View.GONE else View.VISIBLE)
        if (spend == null) return
        rv.setTextViewText(R.id.month_today, "Today ${formatAmount(spend.total, view.currency)} · ${daySpendDetail(spend.entries)}")
        rv.setTextViewText(R.id.month_perday, formatAmount(Math.round(view.monthSpent / view.day.dayOfMonth).toDouble(), view.currency) + "/day")
    }

    private fun fillLarge(context: Context, rv: RemoteViews, view: TodayView) {
        val spend = view.spend
        val show = if (spend == null) View.GONE else View.VISIBLE
        for (id in listOf(R.id.large_divider, R.id.large_header, R.id.large_chips)) rv.setViewVisibility(id, show)
        if (spend == null) {
            rv.setViewVisibility(R.id.large_row1, View.GONE); rv.setViewVisibility(R.id.large_row2, View.GONE)
            return
        }
        rv.setTextViewText(R.id.large_total, formatAmount(spend.total, view.currency))
        val rows = listOf(Triple(R.id.large_row1, R.id.large_title1, listOf(R.id.large_sub1, R.id.large_amount1)), Triple(R.id.large_row2, R.id.large_title2, listOf(R.id.large_sub2, R.id.large_amount2)))
        rows.forEachIndexed { i, (row, title, rest) ->
            val e = view.todays.getOrNull(i)
            // With nothing today the first row says so, in words
            rv.setViewVisibility(row, if (e != null || i == 0) View.VISIBLE else View.GONE)
            rv.setTextViewText(title, e?.let { it.description.ifBlank { it.category } } ?: "Nothing added yet")
            rv.setTextViewText(rest[0], e?.let { "${it.category} · ${it.payment}" } ?: "")
            rv.setTextViewText(rest[1], e?.let { formatAmount(it.amount, view.currency) } ?: "")
        }
        listOf(R.id.large_chip1, R.id.large_chip2, R.id.large_chip3).zip(QUICK_ADD).forEach { (id, chip) ->
            rv.setTextViewText(id, "+ ${chip.first}")
            rv.setContentDescription(id, "Add ${chip.first} expense")
            rv.setOnClickPendingIntent(id, open(context, "add?cat=${Uri.encode(chip.second)}"))
        }
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
        Intent(Intent.ACTION_VIEW, "$LINK_SCHEME://$page".toUri(), context, SpendWiseActivity::class.java)
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
