package com.spendwise.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// The Dashboard's month at a glance (spec §6.2). Ported from dashboard.js › renderHome.

sealed interface Meter {
    /** Spent against the monthly budget. */
    data class Budget(val state: BudgetState, val total: Double) : Meter
    /** No budget: spent against this month's income. */
    data class Income(val ratio: Double, val income: Double, val spent: Double) : Meter
}

/** Today against the month's daily average; fractions are of the meter's width. */
data class TodayCompare(val fill: Double, val averageAt: Double, val over: Boolean, val gap: Double)

data class CategoryBar(
    val category: String,
    val amount: Double,
    /** Share of the month's spending, 0–100. */
    val share: Double,
    val limit: Double?,
    val cap: BudgetState?,
) {
    /** The bar's fill: against the category's own limit when it has one, else its share. */
    val fill get() = cap?.let { minOf(it.ratio, 1.0) } ?: (share / 100)
}

data class HomeFigures(
    val monthName: String,
    val spent: Double,
    val invested: Double,
    val income: Double,
    val incomeCount: Int,
    val budget: Budget,
    val meter: Meter?,
    /** Spent per day so far, and the month-end forecast. null until something is spent. */
    val perDay: Double?,
    val forecastTotal: Double?,
    /** All of today's entries, investments included (the count shows them all). */
    val todays: List<Expense>,
    val todaySpent: Double,
    val average: Double,
    val todayCompare: TodayCompare?,
    /** income − spent, or null without income. */
    val saved: Double?,
    val owe: Double,
    val owed: Double,
    val score: MoneyScore?,
    val categories: List<CategoryBar>,
    /** Repeating bills due this month, not yet entered or skipped. */
    val due: List<DueBill>,
    val goals: List<GoalRow>,
)

/** JS `n.toFixed(0)`: the exact binary value rounded, ties to the larger. */
fun toFixed0(n: Double): String = BigDecimal(n).setScale(0, RoundingMode.HALF_UP).toPlainString()

fun homeFigures(
    expenses: List<Expense>,
    income: List<Income>,
    debts: List<Debt>,
    settings: UserSettings,
    holdings: List<Holding>,
    today: LocalDate,
    skipped: SkippedBill = NOTHING_SKIPPED,
): HomeFigures {
    val todayS = today.toString()
    val ms = todayS.take(8) + "01"
    val monthAll = expenses.filter { it.date >= ms && it.date <= todayS }
    val monthExp = monthAll.filter { isSpending(it.category) }
    val monthInc = income.filter { it.date >= ms && it.date <= todayS }
    val spent = monthExp.sumOf { it.amount }
    val invested = monthAll.sumOf { it.amount } - spent
    val incomeTotal = monthInc.sumOf { it.amount }
    val day = today.dayOfMonth
    val monthDays = today.lengthOfMonth()
    val budget = settings.budget

    val limit = budgetState(spent, budget.total)
    val meter = when {
        limit != null -> Meter.Budget(limit, budget.total!!)
        incomeTotal > 0 -> Meter.Income(spent / incomeTotal, incomeTotal, spent)
        else -> null
    }

    val todays = expenses.filter { it.date == todayS }
    val todaySpent = todays.filter { isSpending(it.category) }.sumOf { it.amount }
    val average = if (day > 1) (spent - todaySpent) / (day - 1) else 0.0
    val todayCompare = if (todays.isNotEmpty() && average > 0) {
        // room past the larger, so the average tick never sits on the end
        val scale = maxOf(todaySpent, average) * 1.1
        TodayCompare(todaySpent / scale, average / scale, todaySpent > average, Math.abs(todaySpent - average))
    } else null

    val active = debts.filter { !it.settled }
    val owe = active.filter { it.type == I_OWE }.sumOf { debtLeft(it.amount, it.paid) }
    val owed = active.filter { it.type == THEY_OWE }.sumOf { debtLeft(it.amount, it.paid) }

    val goals = goalRows(settings.goals, linkedToGoals(portfolio(holdings, expenses, todayS).rows), todayS)
    val score = financeScore(
        income = incomeTotal, spent = spent, invested = invested, budget = budget.total,
        monthShare = day.toDouble() / monthDays, owe = owe,
        goals = goals.map { it.pace.level },
    )

    // Top five categories, plus any close to or past its own limit so a warning is never cut off.
    // LinkedHashMap keeps first-seen order, so equal amounts keep the web's (stable sort) order.
    val byCat = LinkedHashMap<String, Double>()
    monthExp.forEach { byCat[it.category] = (byCat[it.category] ?: 0.0) + it.amount }
    val categories = byCat.entries.sortedByDescending { it.value }
        .filterIndexed { i, (c, amount) -> i < 5 || (budgetState(amount, budget.cats[c])?.level ?: BudgetLevel.OK) != BudgetLevel.OK }
        .map { (c, amount) ->
            CategoryBar(c, amount, if (spent > 0) amount / spent * 100 else 0.0, budget.cats[c], budgetState(amount, budget.cats[c]))
        }

    return HomeFigures(
        monthName = today.month.getDisplayName(TextStyle.FULL, Locale.US),
        spent = spent,
        invested = invested,
        income = incomeTotal,
        incomeCount = monthInc.size,
        budget = budget,
        meter = meter,
        perDay = if (spent > 0) spent / day else null,
        forecastTotal = if (spent > 0) forecast(expenses, todayS, skipped).total else null,
        todays = todays,
        todaySpent = todaySpent,
        average = average,
        todayCompare = todayCompare,
        saved = if (incomeTotal > 0) incomeTotal - spent else null,
        owe = owe,
        owed = owed,
        score = score,
        categories = categories,
        due = dueRecurring(expenses, todayS, skipped),
        goals = goals,
    )
}
