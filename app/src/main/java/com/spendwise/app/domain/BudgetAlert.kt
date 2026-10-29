package com.spendwise.app.domain

// Android-only budget alerts: a notification at 80% of the monthly budget and another past 100%, each once
// a month per account. Uses the Dashboard's own measure (spending this month, budgetState's levels).

data class BudgetAlert(val level: BudgetLevel, val title: String, val text: String)

/**
 * The alert due now, or null. [lastSent] is the "YYYY-MM:NEAR|OVER" last sent to this account on this
 * device; a new month starts over, and NEAR can still be followed by OVER.
 */
fun budgetAlert(expenses: List<Expense>, budget: Double?, today: String, lastSent: String?, money: (Double) -> String): BudgetAlert? {
    val month = today.take(7)
    val spent = monthSpending(expenses, today)
    val state = budgetState(spent, budget) ?: return null
    if (state.level == BudgetLevel.OK) return null
    val sent = lastSent?.takeIf { it.startsWith("$month:") }?.substringAfter(':')
    if (sent == BudgetLevel.OVER.name || sent == state.level.name) return null
    return if (state.level == BudgetLevel.OVER) {
        BudgetAlert(state.level, "You're over this month's budget", "${money(spent)} spent of ${money(budget!!)}, ${money(-state.left)} over.")
    } else {
        BudgetAlert(state.level, "You've used ${(state.ratio * 100).toInt()}% of this month's budget", "${money(spent)} spent of ${money(budget!!)}, ${money(state.left)} left.")
    }
}

/** What to store once [alert] is sent in [today]'s month. */
fun budgetAlertSent(alert: BudgetAlert, today: String): String = "${today.take(7)}:${alert.level.name}"
