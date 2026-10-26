package com.spendwise.app.domain

import java.time.YearMonth

// Repeating expenses and EMIs (spec §6.5). Ported from helpers.js "Repeating expenses" block.
// Order matters for ties (newest date wins, first seen breaks ties), so callers pass expenses in the
// web's order: date descending, then Firestore document order.

fun recurringKey(e: Expense) = "${normalizeCategory(e.category)}|${e.description.trim().lowercase()}"

private class Series(
    val latest: Map<String, Expense>,
    /** Insertion-ordered like a JS Map, which forecast's "next month" loop walks. */
    val template: LinkedHashMap<String, Expense>,
    val count: Map<String, Int>,
)

private fun recurringSeries(expenses: List<Expense>): Series {
    val latest = HashMap<String, Expense>()
    val template = LinkedHashMap<String, Expense>()
    val count = HashMap<String, Int>()
    for (e in expenses) {
        if (e.date.isEmpty()) continue
        val key = recurringKey(e)
        if (latest[key].let { it == null || e.date > it.date }) latest[key] = e
        if (!e.repeat.isNullOrEmpty() && template[key].let { it == null || e.date > it.date }) template[key] = e
        count[key] = (count[key] ?: 0) + 1
    }
    return Series(latest, template, count)
}

/** An EMI's payments so far, or null for a series without a set number of payments. */
data class Emi(val paid: Int, val total: Int)

private fun emiOf(t: Expense, entries: Int): Emi? {
    val total = t.emiTotal ?: 0
    if (total < 1) return null
    return Emi(minOf(entries + (t.emiPaidBefore ?: 0), total), total)
}

private fun Emi?.done() = this != null && paid >= total

fun daysIn(month: String): Int = YearMonth.parse(month).lengthOfMonth()

/** The template's day in [month] ("YYYY-MM"); the 31st falls on a shorter month's last day. */
private fun dueIn(t: Expense, month: String): String {
    val day = minOf(t.date.substring(8, 10).toInt(), daysIn(month))
    return "$month-${day.toString().padStart(2, '0')}"
}

/** [skipped] (key, "YYYY-MM") hides a bill for a month ("Skip month", device-local, spec §9.4). */
typealias SkippedBill = (key: String, month: String) -> Boolean

val NOTHING_SKIPPED: SkippedBill = { _, _ -> false }

data class DueBill(val key: String, val template: Expense, val due: String, val emi: Emi?)

/** Series due this month: active, nothing entered yet this month, and their day has come. */
fun dueRecurring(expenses: List<Expense>, today: String, skipped: SkippedBill = NOTHING_SKIPPED): List<DueBill> {
    val month = today.take(7)
    val s = recurringSeries(expenses)
    val due = mutableListOf<DueBill>()
    for ((key, t) in s.template) {
        if (t.repeat != "monthly" || s.latest.getValue(key).date.take(7) >= month || skipped(key, month)) continue
        val emi = emiOf(t, s.count.getValue(key))
        if (emi.done()) continue
        val day = dueIn(t, month)
        if (today < day) continue
        due += DueBill(key, t, day, emi)
    }
    return due.sortedWith(compareBy<DueBill> { it.due }.thenByDescending { it.template.amount })
}

data class UpcomingBill(val key: String, val title: String, val amount: Double, val due: String)

fun billTitle(t: Expense) = if (t.description.isNotEmpty() && t.description != "-") t.description else t.category

/** Each active series' next due date from today on: this month's unless entered, skipped or passed, else next month's. */
fun upcomingBills(expenses: List<Expense>, today: String, skipped: SkippedBill = NOTHING_SKIPPED): List<UpcomingBill> {
    val month = today.take(7)
    val next = addMonths(month, 1)
    val s = recurringSeries(expenses)
    val bills = mutableListOf<UpcomingBill>()
    for ((key, t) in s.template) {
        val entered = s.latest.getValue(key).date.take(7)
        if (t.repeat != "monthly" || entered >= next || emiOf(t, s.count.getValue(key)).done()) continue
        var due = dueIn(t, month)
        if (entered >= month || skipped(key, month) || due < today) due = dueIn(t, next)
        bills += UpcomingBill(key, billTitle(t), t.amount, due)
    }
    // shortcut: JS sorts with localeCompare; keys only differ in ways where code-unit order agrees for ordinary text
    return bills.sortedBy { it.key }
}

/** One EMI's schedule. [next] and [ends] are null/the last month entered once it's paid off. */
data class EmiPlan(
    val key: String,
    val template: Expense,
    val paid: Int,
    val total: Int,
    val monthly: Double,
    /** What's still to pay: payments to go × the monthly amount. */
    val left: Double,
    val done: Boolean,
    val title: String,
    val next: String?,
    /** "YYYY-MM" of the last payment. */
    val ends: String,
)

/**
 * emiPlans: every series with a set number of payments, except one stopped before it was paid off.
 * Running ones first, by next payment; paid-off ones by the month they ended.
 */
fun emiPlans(expenses: List<Expense>, today: String): List<EmiPlan> {
    val month = today.take(7)
    val s = recurringSeries(expenses)
    val plans = mutableListOf<EmiPlan>()
    for ((key, t) in s.template) {
        val emi = emiOf(t, s.count.getValue(key)) ?: continue
        if (t.repeat != "monthly" && !emi.done()) continue
        val toGo = emi.total - emi.paid
        val entered = s.latest.getValue(key).date.take(7)
        val from = if (entered >= month) addMonths(month, 1) else month
        plans += EmiPlan(
            key, t, emi.paid, emi.total, t.amount, toGo * t.amount, toGo == 0, billTitle(t),
            next = if (toGo > 0) dueIn(t, from) else null,
            ends = if (toGo > 0) addMonths(from, toGo - 1) else entered,
        )
    }
    return plans.sortedWith(compareBy<EmiPlan> { it.done }.thenBy { it.next ?: it.ends })
}

/** The plan's progress line on the Add page's EMIs card (dashboard.js › renderEmis). */
fun EmiPlan.meta(today: java.time.LocalDate, whole: (Double) -> String): String {
    val facts = if (done) listOf("Paid off ${monthYear(ends)}") else listOf(
        "${whole(left)} left",
        if (next!! < today.toString()) "Due ${shortDay(next, today)}" else "Next ${shortDay(next, today)}",
        "Ends ${monthYear(ends)}",
    )
    return "$paid of $total paid · " + facts.joinToString(" · ")
}

/** The bar's width, at least 2% so a new plan still shows. */
val EmiPlan.percent get() = maxOf(Math.round(paid.toDouble() / total * 100).toInt(), 2)

/** "Due this month" row meta (dashboard.js › renderDue). */
fun DueBill.meta(today: java.time.LocalDate) =
    (emi?.let { "EMI ${it.paid + 1} of ${it.total} · " } ?: "") + "${template.category} · due ${shortDay(due, today)}"

/** The Add page's EMIs card header: what the running ones cost a month, or "All paid off". */
fun emiHeader(plans: List<EmiPlan>, whole: (Double) -> String): String {
    val running = plans.filter { !it.done }
    return if (running.isNotEmpty()) "${whole(running.sumOf { it.monthly })} a month" else "All paid off"
}

// ── Forecast (spec §6.6) ──

private fun isFixed(e: Expense) = e.repeat == "monthly" || (e.emiTotal ?: 0) > 0

data class NextMonth(val month: String, val fixed: Double, val bills: Int, val usual: Double, val total: Double)

data class Forecast(
    val month: String,
    val spent: Double,
    val perDay: Double,
    val daysLeft: Int,
    val billsDue: List<UpcomingBill>,
    val billsTotal: Double,
    val usualLeft: Double,
    val oneOffs: Int,
    val total: Double,
    val lastMonthSpent: Double,
    val next: NextMonth,
)

/** Month-end forecast: spent so far + bills still due + the usual flexible spending per day for the days left. */
fun forecast(expenses: List<Expense>, today: String, skipped: SkippedBill = NOTHING_SKIPPED): Forecast {
    val month = today.take(7)
    val next = addMonths(month, 1)
    val spending = expenses.filter { it.date.isNotEmpty() && isSpending(it.category) }
    val spent = spending.filter { it.date.take(7) == month && it.date <= today }.sumOf { it.amount }

    // Usual flexible spending per day: the last 90 days, or since the first entry for a newer account
    val start = java.time.LocalDate.parse(today).minusDays(89).toString()
    val first = spending.fold(today) { m, e -> if (e.date < m) e.date else m }
    val from = if (first > start) first else start
    val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(from), java.time.LocalDate.parse(today)) + 1
    val flexEntries = spending.filter { !isFixed(it) && it.date >= from && it.date <= today }
    val flexTotal = flexEntries.sumOf { it.amount }
    val cap = if (flexEntries.size >= 30) flexTotal * 0.05 else Double.POSITIVE_INFINITY
    val oneOffs = flexEntries.filter { it.amount > cap }
    val flexible = flexTotal - oneOffs.sumOf { it.amount }
    val perDay = if (days > 0) flexible / days else 0.0

    // Bills still to pay this month: due ones not yet entered, and ones whose day hasn't come
    val due = dueRecurring(spending, today, skipped).map { UpcomingBill(it.key, billTitle(it.template), it.template.amount, it.due) }
    val later = upcomingBills(spending, today, skipped).filter { b -> b.due.take(7) == month && due.none { it.key == b.key } }
    val billsDue = (due + later).sortedBy { it.due }
    val billsTotal = billsDue.sumOf { it.amount }
    val daysLeft = daysIn(month) - today.substring(8, 10).toInt()
    val usualLeft = perDay * daysLeft

    // Next month's running bills: every active series, less EMIs that end this month
    val s = recurringSeries(spending)
    var nextFixed = 0.0
    var nextBills = 0
    for ((key, t) in s.template) {
        if (t.repeat != "monthly") continue
        val emi = emiOf(t, s.count.getValue(key))
        if (emi != null) {
            val payingThisMonth = if (s.latest.getValue(key).date.take(7) < month && billsDue.any { it.key == key }) 1 else 0
            if (emi.total - emi.paid - payingThisMonth < 1) continue
        }
        nextFixed += t.amount
        nextBills++
    }
    val lastMonth = addMonths(month, -1)
    val usualNext = perDay * daysIn(next)
    return Forecast(
        month = month, spent = spent, perDay = perDay, daysLeft = daysLeft, billsDue = billsDue, billsTotal = billsTotal,
        usualLeft = usualLeft, oneOffs = oneOffs.size, total = spent + billsTotal + usualLeft,
        lastMonthSpent = spending.filter { it.date.take(7) == lastMonth }.sumOf { it.amount },
        next = NextMonth(next, nextFixed, nextBills, usualNext, nextFixed + usualNext),
    )
}
