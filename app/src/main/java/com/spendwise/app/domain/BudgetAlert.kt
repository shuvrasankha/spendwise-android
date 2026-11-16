package com.spendwise.app.domain

// Android-only budget alerts: a notification at 80% of a monthly limit and another past 100%, each once a month
// per account. Covers the total budget and each category limit (settings.budget.cats), using the Dashboard's
// own measure (spending this month, budgetState's levels).

/** [category] is [TOTAL] for the whole budget, otherwise the category's full name. */
data class BudgetAlert(val category: String, val level: BudgetLevel, val title: String, val text: String)

const val TOTAL = "*"

/**
 * The alerts due now, total first. [sent] holds "YYYY-MM:category:NEAR|OVER" for alerts already sent to this
 * account on this device; a new month starts over, and NEAR can still be followed by OVER.
 */
fun budgetAlerts(expenses: List<Expense>, budget: Budget, today: String, sent: Set<String>, money: (Double) -> String): List<BudgetAlert> {
    val month = today.take(7)
    val monthExp = expenses.filter { it.date >= "$month-01" && it.date <= today && isSpending(it.category) }
    val limits = listOfNotNull(budget.total?.let { TOTAL to it }) + budget.cats.toList()
    return limits.mapNotNull { (cat, limit) ->
        val spent = monthExp.filter { cat == TOTAL || it.category == cat }.sumOf { it.amount }
        val state = budgetState(spent, limit) ?: return@mapNotNull null
        if (state.level == BudgetLevel.OK || "$month:$cat:${BudgetLevel.OVER}" in sent || "$month:$cat:${state.level}" in sent) return@mapNotNull null
        val what = if (cat == TOTAL) "this month's budget" else "your $cat limit"
        if (state.level == BudgetLevel.OVER) {
            BudgetAlert(cat, state.level, "You're over $what", "${money(spent)} spent of ${money(limit)}, ${money(-state.left)} over.")
        } else {
            BudgetAlert(cat, state.level, "You've used ${(state.ratio * 100).toInt()}% of $what", "${money(spent)} spent of ${money(limit)}, ${money(state.left)} left.")
        }
    }
}

/** What to store once [alert] is sent in [today]'s month. */
fun budgetAlertSent(alert: BudgetAlert, today: String): String = "${today.take(7)}:${alert.category}:${alert.level.name}"
