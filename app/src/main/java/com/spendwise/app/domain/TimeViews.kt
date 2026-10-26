package com.spendwise.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// The Dashboard's "Spending over time" (spec §2.10). Ported from dashboard.js › renderDashboardTable (the
// override), getTrendData, getPeriodExpenses, updatePeriodSummary and populateYearPicker. The web's quirks
// are kept on purpose so both clients agree (spec §9.1, §9.2).

enum class TimeTab { DAILY, WEEKLY, MONTHLY, YEARLY }

/** The month and year picker. null is "All Months" / "All Years". [month] is "01".."12", [year] "2026". */
data class Period(val month: String?, val year: String?) {
    companion object {
        fun current(today: LocalDate) = Period(today.monthValue.toString().padStart(2, '0'), today.year.toString())
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** getPeriodExpenses: every expense in the picked month and year. Future-dated entries count. */
fun periodExpenses(expenses: List<Expense>, period: Period): List<Expense> = expenses.filter { e ->
    e.date.isNotEmpty() &&
        (period.year == null || e.date.take(4) == period.year) &&
        (period.month == null || e.date.length >= 7 && e.date.substring(5, 7) == period.month)
}

data class PeriodSummary(val total: Double, val count: Int, val averagePerDay: Double, val largest: Double)

/** The summary bar follows the picker only, whatever the tab, and leaves out investments. */
fun periodSummary(expenses: List<Expense>, period: Period): PeriodSummary {
    val rows = periodExpenses(expenses, period).filter { isSpending(it.category) }
    val total = rows.sumOf { it.amount }
    val days = rows.map { it.date }.toSet().size
    return PeriodSummary(total, rows.size, if (days > 0) total / days else 0.0, rows.maxOfOrNull { it.amount } ?: 0.0)
}

/** The table's rows. Unlike the chart and the summary, it includes investments (spec §9.2). */
fun tableRows(expenses: List<Expense>, tab: TimeTab, period: Period, today: LocalDate): List<Expense> {
    val todayS = today.toString()
    // The yearly tab ignores the picked month
    val periodRows = if (tab == TimeTab.YEARLY) periodExpenses(expenses, period.copy(month = null)) else periodExpenses(expenses, period)
    return when (tab) {
        TimeTab.DAILY -> periodRows.filter { it.date == todayS }
        TimeTab.WEEKLY -> weekStart(today).let { ws -> periodRows.filter { it.date >= ws && it.date <= todayS } }
        TimeTab.MONTHLY -> {
            // "All Months" or "All Years" falls back to the current one
            val year = period.year ?: today.year.toString()
            val month = period.month ?: today.monthValue.toString().padStart(2, '0')
            val start = "$year-$month-01"
            val end = "$year-$month-${daysIn("$year-$month").toString().padStart(2, '0')}"
            periodRows.filter { it.date >= start && it.date <= end }
        }
        TimeTab.YEARLY -> periodRows
    }
}

data class Trend(val labels: List<String>, val values: List<Double>)

/**
 * The line chart. Daily and Weekly always show the last 7 days / 4 weeks, ignoring the picker (spec §9.1);
 * Monthly shows the picked year (or this year); Yearly every year with spending.
 */
fun trend(expenses: List<Expense>, tab: TimeTab, period: Period, today: LocalDate): Trend {
    val spending = expenses.filter { isSpending(it.category) }
    fun sum(match: (String) -> Boolean) = spending.filter { it.date.isNotEmpty() && match(it.date) }.sumOf { it.amount }

    val points: List<Pair<String, Double>> = when (tab) {
        TimeTab.DAILY -> (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val ds = d.toString()
            "${DAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth}" to sum { it == ds }
        }
        TimeTab.WEEKLY -> (3 downTo 0).map { back ->
            val start = today.minusDays(back * 7L).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val end = start.plusDays(6)
            val startMonth = MONTHS[start.monthValue - 1]
            val label = if (start.month == end.month) {
                "${start.dayOfMonth}–${end.dayOfMonth} $startMonth"
            } else {
                "${start.dayOfMonth} $startMonth–${end.dayOfMonth} ${MONTHS[end.monthValue - 1]}"
            }
            val (s, e) = start.toString() to end.toString()
            label to sum { it >= s && it <= e }
        }
        TimeTab.MONTHLY -> {
            val year = period.year ?: today.year.toString()
            (1..12).map { m ->
                val prefix = "$year-${m.toString().padStart(2, '0')}"
                MONTHS[m - 1] to sum { it.take(7) == prefix }
            }
        }
        TimeTab.YEARLY -> spending.filter { it.date.isNotEmpty() }.map { it.date.take(4) }.toSortedSet()
            .map { year -> year to sum { it.take(4) == year } }
    }
    return Trend(points.map { it.first }, points.map { it.second })
}

/** populateYearPicker: this year plus every year with an expense, newest first ("All Years" is null). */
fun yearOptions(expenses: List<Expense>, today: LocalDate): List<String> =
    (expenses.mapNotNull { e -> e.date.take(4).toIntOrNull()?.takeIf { e.date.isNotEmpty() } } + today.year)
        .toSortedSet(compareByDescending { it }).map { it.toString() }
