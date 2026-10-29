package com.spendwise.app.reminder

import android.content.Context
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.budgetAlert
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
 * Budget alerts at 80% and past 100% of the monthly budget (domain/BudgetAlert.kt). Follows the live expenses,
 * so it alerts the moment a save crosses the line.
 */
// shortcut: runs while the app's process is alive; spending added on the web alerts when the app next opens.
// Upgrade by having the Worker send it, if alerts need to reach a phone that never opens the app.
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
        combine(expenses.expenses(serverOnly = true).catch { }, settings.settings().catch { }) { list, s -> list to s.budget.total }
            .onEach { (list, budget) -> runCatching { check(list, budget) } }
            .launchIn(scope)
    }

    private suspend fun check(list: List<Expense>, budget: Double?) {
        val uid = auth.currentUid ?: return
        val today = todayStr()
        val currency = prefs.currency.first()
        val alert = budgetAlert(list, budget, today, prefs.budgetAlertSent(uid).first()) { formatWhole(it, currency) } ?: return
        // Marked sent first: with notifications off it is dropped, not retried on every snapshot
        prefs.setBudgetAlertSent(uid, budgetAlertSent(alert, today))
        Notifications.message(context, Notifications.BUDGET_CHANNEL, "budget-alert", alert.title, alert.text, "dashboard")
    }
}
