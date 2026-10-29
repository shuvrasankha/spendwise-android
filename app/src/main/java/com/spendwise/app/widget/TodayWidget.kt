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
import com.spendwise.app.MainActivity
import com.spendwise.app.R
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.expenses.expenseFrom
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.DaySpend
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
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Home-screen widget: today's spending, with + for Add expense. Android only; nothing new is stored. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        TodayWidgetWorker.enqueue(context)
    }
}

/** What the widget shows: null spend means signed out. */
private data class TodayView(val spend: DaySpend?, val currency: Currency, val day: LocalDate)

private object TodayWidgetView {
    private val dayLabel = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun installed(context: Context) = ids(context).isNotEmpty()

    private fun ids(context: Context) =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayWidget::class.java))

    fun show(context: Context, view: TodayView) {
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
        rv.setOnClickPendingIntent(android.R.id.background, open(context, "dashboard"))
        AppWidgetManager.getInstance(context).updateAppWidget(ComponentName(context, TodayWidget::class.java), rv)
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
}

/** Reads today's expenses when the app isn't running: the 30-minute refresh, and a widget just placed. */
class TodayWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, TodayWidgetEntryPoint::class.java)
        val currency = deps.prefs().currency.first()
        val day = LocalDate.now()
        val uid = deps.auth().currentUser?.uid
            ?: return Result.success().also { TodayWidgetView.show(applicationContext, TodayView(null, currency, day)) }
        return try {
            // One day's documents: the same uid + date query as the reminder; offline it answers from the cache
            val docs = FirebaseFirestore.getInstance().collection("expenses")
                .whereEqualTo("uid", uid).whereEqualTo("date", todayStr(day)).get().await()
            val expenses = docs.documents.map { expenseFrom(it.id, it.data.orEmpty()) }
            TodayWidgetView.show(applicationContext, TodayView(daySpend(expenses, todayStr(day)), currency, day))
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
    private val prefs: DevicePrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // shortcut: starts only if a widget is already placed at launch; one placed later is filled by the worker
    // and goes live from the next launch. Upgrade with onEnabled starting the sync if that gap shows.
    fun start() {
        if (!TodayWidgetView.installed(context)) return
        combine(auth.authState.filter { it !is AuthState.Loading }, expenses.expenses().catch { emit(emptyList()) }, prefs.currency) { state, list, currency ->
            val day = LocalDate.now()
            TodayView(if (state is AuthState.SignedIn) daySpend(list, todayStr(day)) else null, currency, day)
        }
            .distinctUntilChanged()
            .onEach { TodayWidgetView.show(context, it) }
            .launchIn(scope)
    }
}
