package com.spendwise.app.reminder

import android.content.Context
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.Budget
import com.spendwise.app.domain.TOTAL
import com.spendwise.app.domain.budgetAlerts
import com.spendwise.app.domain.budgetAlertSent
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.format.formatWhole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Budget alerts at 80% and past 100% of the monthly budget and of each category limit (domain/BudgetAlert.kt).
 * Follows the live expenses, so it alerts the moment a save crosses the line; [DailyWorker] also calls [check]
 * once a day, so spending added on the web alerts even when the app isn't opened.
 */
@Singleton
class BudgetAlertSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
    private val prefs: DevicePrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        // From the server only: a half-filled cache would under-count, never over-count, but the server's is the one to trust
        combine(expenses.expenses(serverOnly = true).catch { }, settings.settings().catch { }) { list, s -> list to s.budget }
            .onEach { (list, budget) -> runCatching { check(list, budget) } }
            .launchIn(scope)
    }

    suspend fun check(list: List<Expense>, budget: Budget) {
        val uid = auth.currentUid ?: return
        val today = todayStr()
        val currency = prefs.currency.first()
        val sent = prefs.budgetAlertsSent(uid).first()
        val alerts = budgetAlerts(list, budget, today, sent) { formatWhole(it, currency) }
        if (alerts.isEmpty()) return
        // Marked sent first: with notifications off they're dropped, not retried on every snapshot. Older months are pruned.
        prefs.setBudgetAlertsSent(uid, sent.filter { it.startsWith(today.take(7)) }.toSet() + alerts.map { budgetAlertSent(it, today) })
        alerts.forEach { a ->
            val tag = if (a.category == TOTAL) "budget-alert" else "budget-alert:${a.category}"
            Notifications.message(context, Notifications.BUDGET_CHANNEL, tag, a.title, a.text, "dashboard")
        }
    }
}
