package com.spendwise.app.domain

import java.time.YearMonth

/** One calendar day: its spending [total] and [level] 0 none, 1 lower, 2 higher (at or above the month's average spending day). */
data class CalendarDay(val date: String, val total: Double, val level: Int)

/** A month laid out Monday-first: leading nulls pad the first week; [days] holds one entry per day. */
data class CalendarMonth(val lead: Int, val days: List<CalendarDay>, val total: Double)

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** Spending per day for [month] ("YYYY-MM"), skipping investments, bad dates and non-positive amounts like the web. */
fun spendingCalendar(expenses: List<Expense>, month: String): CalendarMonth {
    val ym = YearMonth.parse(month)
    val byDate = HashMap<String, Double>()
    for (e in expenses) {
        if (!isSpending(e.category) || !DATE.matches(e.date) || !e.date.startsWith(month) || e.amount <= 0) continue
        byDate.merge(e.date, e.amount, Double::plus)
    }
    val spent = byDate.values
    val mean = if (spent.isEmpty()) 0.0 else spent.sum() / spent.size
    val days = (1..ym.lengthOfMonth()).map { d ->
        val date = "%s-%02d".format(month, d)
        val total = byDate[date] ?: 0.0
        CalendarDay(date, total, if (total <= 0) 0 else if (total >= mean) 2 else 1)
    }
    return CalendarMonth(ym.atDay(1).dayOfWeek.value - 1, days, spent.sum())
}
