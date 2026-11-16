package com.spendwise.app.domain

import java.time.LocalDate
import kotlin.math.ceil

// Android-only repayment plans (debts.plan, optional; the web ignores it and keeps it, since it only updates
// debts by field and its backup copies whole docs). No interest: these are debts between people.

const val PLAN_WEEK = "week"
const val PLAN_MONTH = "month"

/** `debts.plan`: [amount] every [every] ("week" | "month"), from [start] ("YYYY-MM-DD"). */
data class DebtPlan(val amount: Double, val every: String, val start: String)

/** [schedule] is what's still to pay, date to amount; [ends] its last date; [behind] what should have been paid by now and wasn't. */
data class Payoff(val schedule: List<Pair<String, Double>>, val ends: String?, val behind: Double)

/** The [k]th instalment's date; monthly ones keep the start's day, on a shorter month's last day. */
fun planDate(plan: DebtPlan, k: Int): String {
    val start = LocalDate.parse(plan.start)
    return (if (plan.every == PLAN_WEEK) start.plusWeeks(k.toLong()) else start.plusMonths(k.toLong())).toString()
}

/** What's left of [d], paid [plan.amount] at a time from the next instalment due today or later. */
fun payoff(d: Debt, plan: DebtPlan, today: String): Payoff {
    val left = debtLeft(d.amount, d.paid)
    var due = 0 // instalments whose date has passed
    while (due < 10_000 && planDate(plan, due) < today) due++
    // Paid on the due day: today's instalment is done, the next one is next period.
    // shortcut: a payment made early (before the due day) isn't credited to that instalment; the estimate still
    // counts it in what's left. Upgrade to per-period matching if testers find the next date confusing.
    val first = if (planDate(plan, due) == today && d.paid.any { it.date == today }) due + 1 else due
    val paidSince = d.paid.filter { it.date >= plan.start }.sumOf { it.amount }
    val owedAtStart = debtLeft(d.amount, d.paid.filter { it.date < plan.start })
    val behind = round2(maxOf(minOf(due * plan.amount, owedAtStart) - paidSince, 0.0))
    if (left <= 0 || plan.amount <= 0) return Payoff(emptyList(), null, 0.0)
    val n = ceil(round2(left / plan.amount)).toInt().coerceAtMost(600)
    val schedule = (0 until n).map { i -> planDate(plan, first + i) to if (i < n - 1) plan.amount else round2(left - plan.amount * (n - 1)) }
    return Payoff(schedule, schedule.lastOrNull()?.first, behind)
}

/** The plan sheet's check, or null when it can be saved. */
fun planError(d: Debt, amount: Double?, start: String): String? = when {
    amount == null || !(amount > 0) -> "Enter how much each payment is."
    amount > debtLeft(d.amount, d.paid) -> "That's more than what's left."
    runCatching { LocalDate.parse(start) }.isFailure -> "Pick a start date."
    else -> null
}
