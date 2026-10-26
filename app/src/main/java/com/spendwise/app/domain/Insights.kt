package com.spendwise.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// The Insights page without AI (spec §1.7, §6.9). Ported from insights-data.js; card wording from
// insights-v2.js. Callers pass expenses in Firestore's document order (by id), as the web loads them, so
// equal amounts keep the web's order. Wording that carries an amount takes [money], the page's whole-number
// money formatter.

enum class InsightPeriod(val label: String) { MONTH("This month"), LAST_MONTH("Last month"), QUARTER("3 months"), YEAR("This year"), CUSTOM("Custom") }

data class PeriodRange(
    val start: String,
    val end: String,
    val label: String,
    val prevStart: String,
    val prevEnd: String,
    val prevLabel: String,
    /** Days that have happened, at least 1, so averages don't divide by future days. */
    val elapsedDays: Int,
    /** The month's length, for "This month" only. */
    val monthDays: Int?,
)

private fun monthName(m: Int) = java.time.Month.of(m).getDisplayName(TextStyle.FULL, Locale.US)
private fun shortMonth(m: Int) = java.time.Month.of(m).getDisplayName(TextStyle.SHORT, Locale.US)
private fun pad(n: Int) = n.toString().padStart(2, '0')
private fun daysBetween(a: String, b: String) = ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)).toInt() + 1
private fun addDays(s: String, n: Int) = LocalDate.parse(s).plusDays(n.toLong()).toString()
private val ymdPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/** insights-data.js › shortDate: "5 Oct". */
fun insightDate(s: String): String = runCatching { LocalDate.parse(s).let { "${it.dayOfMonth} ${shortMonth(it.monthValue)}" } }.getOrDefault(s)

/** periodRange: the period and the one before it, for comparison. Custom dates are "YYYY-MM-DD" or "". */
fun periodRange(period: InsightPeriod, today: LocalDate, customStart: String = "", customEnd: String = ""): Checked<PeriodRange> {
    val y = today.year
    val m = today.monthValue
    val d = today.dayOfMonth
    val t = today.toString()
    val start: String
    val end: String
    val label: String
    var prev: Triple<String, String, String>? = null
    var monthDays: Int? = null
    when (period) {
        InsightPeriod.MONTH -> {
            start = "$y-${pad(m)}-01"; end = t
            label = "${monthName(m)} $y (1–$d ${shortMonth(m)})"
            monthDays = today.lengthOfMonth()
            val p = YearMonth.of(y, m).minusMonths(1)
            prev = Triple("$p-01", "$p-${pad(minOf(d, p.lengthOfMonth()))}", "the same days of ${monthName(p.monthValue)}")
        }
        InsightPeriod.LAST_MONTH -> {
            val l = YearMonth.of(y, m).minusMonths(1)
            start = "$l-01"; end = "$l-${pad(l.lengthOfMonth())}"
            label = "${monthName(l.monthValue)} ${l.year}"
            val p = l.minusMonths(1)
            prev = Triple("$p-01", "$p-${pad(p.lengthOfMonth())}", monthName(p.monthValue))
        }
        InsightPeriod.QUARTER -> {
            start = "${YearMonth.of(y, m).minusMonths(2)}-01"; end = t
            label = "Last 3 months (${insightDate(start)} – ${insightDate(end)})"
        }
        InsightPeriod.YEAR -> {
            start = "$y-01-01"; end = t
            label = "$y so far"
            prev = Triple("${y - 1}-01-01", "${y - 1}-${pad(m)}-${pad(minOf(d, YearMonth.of(y - 1, m).lengthOfMonth()))}", "the same part of ${y - 1}")
        }
        InsightPeriod.CUSTOM -> {
            if (!ymdPattern.matches(customStart) || !ymdPattern.matches(customEnd)) return Checked.Invalid("Pick both a start and an end date.")
            if (customStart > customEnd) return Checked.Invalid("The start date is after the end date.")
            start = customStart; end = customEnd
            label = "${insightDate(start)} – ${insightDate(end)} ${end.take(4)}"
        }
    }
    val (prevStart, prevEnd, prevLabel) = prev ?: run {
        // Quarter and custom: the same number of days right before
        val len = daysBetween(start, end)
        val pe = addDays(start, -1)
        Triple(addDays(pe, -(len - 1)), pe, "the period before")
    }
    val elapsed = daysBetween(start, if (end < t) end else t)
    return Checked.Ok(PeriodRange(start, end, label, prevStart, prevEnd, prevLabel, maxOf(elapsed, 1), monthDays))
}

private fun inRange(date: String, a: String, b: String) = date.isNotEmpty() && date >= a && date <= b
private fun pct(now: Double, before: Double): Double? = if (before > 0) (now - before) / before * 100 else null

data class CategoryShare(val category: String, val amount: Double, val share: Double, val prevAmount: Double, val change: Double?)
data class Mover(val category: String, val amount: Double, val prevAmount: Double, val diff: Double, val change: Double?)
data class BigExpense(val category: String, val amount: Double, val date: String, val description: String)
data class WeekdaySpend(val day: String, val amount: Double)
data class PaymentShare(val name: String, val amount: Double, val share: Double)

data class InsightSummary(
    val spent: Double,
    val earned: Double,
    val invested: Double,
    val txCount: Int,
    val incomeCount: Int,
    val dailyAvg: Double,
    val prevSpent: Double,
    val spendChange: Double?,
    val prevEarned: Double,
    val prevIncomeCount: Int,
    val categories: List<CategoryShare>,
    val movers: List<Mover>,
    val biggest: List<BigExpense>,
    val weekdays: List<WeekdaySpend>,
    val payments: List<PaymentShare>,
    val fixed: Double,
    val youOwe: Double,
    val owedToYou: Double,
    val activeDebts: Int,
) {
    /** hasData: anything to show for the period. */
    val hasData get() = txCount > 0 || incomeCount > 0 || invested > 0 || activeDebts > 0
}

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun byCategory(list: List<Expense>): LinkedHashMap<String, Double> {
    val by = LinkedHashMap<String, Double>() // first-seen order, like a JS object
    list.forEach { by[it.category] = (by[it.category] ?: 0.0) + it.amount }
    return by
}

fun summarize(expenses: List<Expense>, income: List<Income>, debts: List<Debt>, r: PeriodRange): InsightSummary {
    val inPeriod = expenses.filter { inRange(it.date, r.start, r.end) }
    val exp = inPeriod.filter { isSpending(it.category) }
    val inc = income.filter { inRange(it.date, r.start, r.end) }
    val prevExp = expenses.filter { isSpending(it.category) && inRange(it.date, r.prevStart, r.prevEnd) }
    val prevInc = income.filter { inRange(it.date, r.prevStart, r.prevEnd) }
    val spent = exp.sumOf { it.amount }
    val prevSpent = prevExp.sumOf { it.amount }
    val byCat = byCategory(exp)
    val prevByCat = byCategory(prevExp)

    val categories = byCat.map { (c, amount) ->
        CategoryShare(c, amount, if (spent > 0) amount / spent * 100 else 0.0, prevByCat[c] ?: 0.0, pct(amount, prevByCat[c] ?: 0.0))
    }.sortedByDescending { it.amount }

    val all = (byCat.keys + prevByCat.keys).map { c ->
        val amount = byCat[c] ?: 0.0
        val before = prevByCat[c] ?: 0.0
        Mover(c, amount, before, amount - before, pct(amount, before))
    }.filter { Math.abs(it.diff) >= 1 }
    val movers = all.filter { it.diff > 0 }.sortedByDescending { it.diff }.take(2) + all.filter { it.diff < 0 }.sortedBy { it.diff }.take(2)

    val weekdays = DoubleArray(7)
    exp.forEach { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { weekdays[it.dayOfWeek.value - 1] += e.amount } }

    val payBy = LinkedHashMap<String, Double>()
    exp.forEach { val k = it.payment.ifEmpty { "Other" }; payBy[k] = (payBy[k] ?: 0.0) + it.amount }

    // Debts are a current position, not a period total: every unsettled debt counts
    val active = debts.filter { !it.settled }
    return InsightSummary(
        spent = spent,
        earned = inc.sumOf { it.amount },
        invested = inPeriod.sumOf { it.amount } - spent,
        txCount = exp.size,
        incomeCount = inc.size,
        dailyAvg = spent / r.elapsedDays,
        prevSpent = prevSpent,
        spendChange = pct(spent, prevSpent),
        prevEarned = prevInc.sumOf { it.amount },
        prevIncomeCount = prevInc.size,
        categories = categories,
        movers = movers,
        biggest = exp.sortedByDescending { it.amount }.take(3)
            .map { BigExpense(it.category, it.amount, it.date, if (it.description.isNotEmpty() && it.description != "-") it.description else "") },
        weekdays = WEEKDAYS.mapIndexed { i, day -> WeekdaySpend(day, weekdays[i]) },
        payments = payBy.map { (k, v) -> PaymentShare(k, v, if (spent > 0) v / spent * 100 else 0.0) }.sortedByDescending { it.amount },
        fixed = exp.filter { it.repeat == "monthly" || (it.emiTotal ?: 0) > 0 }.sumOf { it.amount },
        youOwe = active.filter { it.type == I_OWE }.sumOf { debtLeft(it.amount, it.paid) },
        owedToYou = active.filter { it.type == THEY_OWE }.sumOf { debtLeft(it.amount, it.paid) },
        activeDebts = active.size,
    )
}

// ── Where it went (pieSlices) ────────────────────────────────────────────────

/** [share] and [start] are of 100; [other] marks a folded "Other" slice (two or more categories). */
data class PieSlice(val label: String, val amount: Double, val other: Boolean, val share: Double, val start: Double)

fun pieSlices(categories: List<CategoryShare>, max: Int = 5): List<PieSlice> {
    val list = categories.filter { it.amount > 0 }
    val total = list.sumOf { it.amount }
    if (!(total > 0)) return emptyList()
    val shown = if (list.size > max) list.take(max - 1) else list
    val rest = list.drop(shown.size)
    val raw = shown.map { Triple(it.category, it.amount, false) } +
        listOfNotNull(rest.takeIf { it.isNotEmpty() }?.let { Triple(if (it.size == 1) it[0].category else "Other", it.sumOf { c -> c.amount }, it.size > 1) })
    var start = 0.0
    return raw.map { (label, amount, other) ->
        val share = amount / total * 100
        PieSlice(label, amount, other, share, start).also { start += share }
    }
}

/** The legend's percentage: "<1" below one percent. */
fun PieSlice.shareText() = if (share < 1) "<1%" else "${toFixed0(share)}%"

// ── Against your budget (budgetReport) ───────────────────────────────────────

data class BudgetCatMonth(val spent: Double, val projected: Double, val limit: Double)

data class BudgetMonth(
    val month: String,
    val label: String,
    val current: Boolean,
    val partial: Boolean,
    val spent: Double,
    val projected: Double,
    val limit: Double?,
    val over: Boolean,
    val cats: Map<String, BudgetCatMonth>,
)

data class BudgetCat(val category: String, val limit: Double, val avg: Double, val overIn: Int, val months: Int, val worst: Double)

data class BudgetReport(
    val total: Double?,
    val months: List<BudgetMonth>,
    val within: Int,
    val judged: Int,
    val spent: Double,
    val limit: Double?,
    val cats: List<BudgetCat>,
)

/**
 * Each month the period touches, against the budget saved now. A month only partly in the period gets that
 * share of the limit; the current month counts whole and is judged on [monthEnd] (the forecast total).
 */
fun budgetReport(expenses: List<Expense>, budget: Budget, r: PeriodRange, today: String, monthEnd: Double?): BudgetReport? {
    val total = budget.total?.takeIf { it > 0 }
    val caps = budget.cats.filter { it.value > 0 }
    if (total == null && caps.isEmpty()) return null
    val spending = expenses.filter { isSpending(it.category) && inRange(it.date, r.start, r.end) }
    val thisMonth = today.take(7)
    val months = generateSequence(r.start.take(7)) { addMonths(it, 1) }.takeWhile { it <= r.end.take(7) }.toList()

    val rows = months.map { m ->
        val ym = YearMonth.parse(m)
        val days = ym.lengthOfMonth()
        val from = maxOf(r.start, "$m-01")
        val to = minOf(r.end, "$m-${pad(days)}")
        val current = m == thisMonth
        val share = if (current) 1.0 else daysBetween(from, to).toDouble() / days
        val list = spending.filter { it.date.take(7) == m }
        val spent = list.sumOf { it.amount }
        val elapsed = if (current) today.substring(8, 10).toInt() else days
        val catSpent = HashMap<String, Double>()
        val catFixed = HashMap<String, Double>()
        list.forEach {
            catSpent[it.category] = (catSpent[it.category] ?: 0.0) + it.amount
            if (it.repeat == "monthly" || (it.emiTotal ?: 0) > 0) catFixed[it.category] = (catFixed[it.category] ?: 0.0) + it.amount
        }
        // A category heads for its bills plus its flexible spending at this month's pace
        fun heading(c: String): Double {
            val s = catSpent[c] ?: return 0.0
            val f = catFixed[c] ?: 0.0
            return if (current) f + (s - f) / elapsed * days else s
        }
        val limit = total?.let { it * share }
        val projected = if (current) maxOf(monthEnd ?: spent, spent) else spent
        BudgetMonth(
            m, shortMonth(ym.monthValue), current, share < 1, spent, projected, limit, limit != null && projected > limit,
            caps.mapValues { (c, cap) -> BudgetCatMonth(catSpent[c] ?: 0.0, heading(c), cap * share) },
        )
    }

    val cats = caps.map { (category, cap) ->
        val per = rows.map { it.cats.getValue(category) }
        BudgetCat(category, cap, per.sumOf { it.projected } / per.size, per.count { it.projected > it.limit }, rows.size, per.maxOf { it.projected - it.limit })
    }.sortedWith(compareByDescending<BudgetCat> { it.overIn }.thenByDescending { it.worst })

    val judged = rows.filter { it.limit != null }
    return BudgetReport(
        total, rows, judged.count { !it.over }, judged.size, spending.sumOf { it.amount },
        total?.let { judged.sumOf { it.limit!! } }, cats,
    )
}

// ── Card wording (insights-v2.js) ────────────────────────────────────────────

/** A big figure and the words after it ("₹2,100" "more spent"). */
data class InsightFigure(val amount: String, val unit: String)

/** A line that rises or falls: [up] is spending going up, shown in the "owed/over" colour. */
data class Change(val text: String, val up: Boolean)

/** "₹2,100 more" / "₹2,100 less" / "the same". */
fun moreLess(d: Double, money: (Double) -> String) = if (Math.abs(d) < 1) "the same" else "${money(Math.abs(d))} ${if (d > 0) "more" else "less"}"

data class CompareCard(val title: String, val none: String?, val figure: InsightFigure?, val change: Change?, val rows: List<Pair<String, String>>)

fun compareCard(s: InsightSummary, r: PeriodRange, money: (Double) -> String): CompareCard {
    val title = "Compared with ${r.prevLabel}"
    if (!(s.prevSpent > 0)) return CompareCard(title, "Nothing to compare yet: no expenses in ${r.prevLabel}.", null, null, emptyList())
    val diff = s.spent - s.prevSpent
    val same = Math.abs(diff) < 1
    val rows = buildList {
        if (s.incomeCount > 0 || s.prevIncomeCount > 0) add("Income" to moreLess(s.earned - s.prevEarned, money))
        if (s.incomeCount > 0 && s.prevIncomeCount > 0) add("Saved" to moreLess((s.earned - s.spent) - (s.prevEarned - s.prevSpent), money))
    }
    return CompareCard(
        title, null,
        InsightFigure(if (same) "Same" else money(Math.abs(diff)), if (same) "spending as before" else "${if (diff > 0) "more" else "less"} spent"),
        s.spendChange?.takeIf { !same }?.let { Change("${toFixed0(Math.abs(it))}% ${if (diff > 0) "higher" else "lower"} than ${r.prevLabel}", diff > 0) },
        rows,
    )
}

data class BudgetMonthRow(val label: String, val current: Boolean, val status: String?, val over: Boolean, val sub: String)
data class BudgetCatRow(val category: String, val sub: String, val value: String, val over: Boolean)

fun BudgetReport.figure(money: (Double) -> String): InsightFigure? {
    if (total == null) return null
    val one = months.singleOrNull()
    if (one != null) {
        val d = one.limit!! - one.projected
        return InsightFigure(money(Math.abs(d)), "${if (d >= 0) "under" else "over"} budget${if (one.current) " at this pace" else ""}")
    }
    return InsightFigure("$within of $judged", "${if (judged == 1) "month" else "months"} within budget")
}

fun BudgetReport.monthRows(money: (Double) -> String): List<BudgetMonthRow> = if (total == null) emptyList() else months.map { m ->
    val limit = m.limit!!
    BudgetMonthRow(
        m.label, m.current,
        status = if (months.size == 1) null else if (m.over) "${money(m.projected - limit)} over" else "${money(limit - m.projected)} under",
        over = m.over,
        sub = if (m.current) "${money(m.spent)} so far · heading for ${money(m.projected)} of ${money(limit)}"
        else "${money(m.spent)} of ${money(limit)}${if (m.partial) " (part of the month)" else ""}",
    )
}

fun BudgetReport.catRows(money: (Double) -> String): List<BudgetCatRow> = cats.map { c ->
    val over = c.overIn > 0
    val one = months.size == 1
    BudgetCatRow(
        c.category,
        sub = if (one) "${if (months[0].current) "Heading for" else "Spent"} ${money(c.avg)} of ${money(c.limit)}" else "About ${money(c.avg)} a month of ${money(c.limit)}",
        value = if (one) (if (over) "${money(c.avg - c.limit)} over" else "Within") else (if (over) "Over ${c.overIn} of ${c.months}" else "Within"),
        over = over,
    )
}

/** "What changed": the row's line under the category, and its signed amount. */
fun Mover.sub(money: (Double) -> String) = when {
    prevAmount == 0.0 -> "New · ${money(amount)}"
    amount == 0.0 -> "None this time · was ${money(prevAmount)}"
    else -> "${money(prevAmount)} → ${money(amount)}"
}

fun Mover.value(money: (Double) -> String) = "${if (diff > 0) "+" else "−"}${money(Math.abs(diff))}"

fun BigExpense.title() = description.ifEmpty { category }
fun BigExpense.meta() = listOf(if (description.isNotEmpty()) category else "", insightDate(date)).filter { it.isNotEmpty() }.joinToString(" · ")

private val DAY_NAMES = mapOf("Mon" to "Mondays", "Tue" to "Tuesdays", "Wed" to "Wednesdays", "Thu" to "Thursdays", "Fri" to "Fridays", "Sat" to "Saturdays", "Sun" to "Sundays")

/** "When you spend", shown from 3 expenses: the top day (the first, on a tie) and each bar's height of 100. */
data class WeekdayCard(val top: WeekdaySpend, val lead: String, val heights: List<Double>)

fun weekdayCard(s: InsightSummary, money: (Double) -> String): WeekdayCard? {
    if (s.txCount < 3) return null
    val top = s.weekdays.reduce { a, b -> if (b.amount > a.amount) b else a }
    val max = top.amount.takeIf { it != 0.0 } ?: 1.0
    val share = if (s.spent > 0) toFixed0(top.amount / s.spent * 100) else "0"
    return WeekdayCard(
        top,
        "Most on ${DAY_NAMES.getValue(top.day)}: ${money(top.amount)}, $share% of this period's spending.",
        s.weekdays.map { maxOf(it.amount / max * 100, if (it.amount != 0.0) 6.0 else 0.0) },
    )
}

/** "How you pay", from 2 expenses: the top 5 methods. */
fun paymentRows(s: InsightSummary, money: (Double) -> String): List<Pair<PaymentShare, String>> =
    if (s.payments.isEmpty() || s.txCount < 2) emptyList() else s.payments.take(5).map { it to "${money(it.amount)} · ${toFixed0(it.share)}%" }

/** "Fixed and flexible": the fixed share of 100, and the two legend lines with their notes. */
data class FixedCard(val share: Double, val fixed: Pair<String, String>, val flexible: Pair<String, String>)

fun fixedCard(s: InsightSummary, money: (Double) -> String): FixedCard? {
    if (!(s.fixed > 0) || !(s.spent > 0)) return null
    val share = s.fixed / s.spent * 100
    return FixedCard(
        share,
        "${money(s.fixed)} fixed" to "Repeating bills and EMIs · ${toFixed0(share)}%",
        "${money(s.spent - s.fixed)} flexible" to "Everything else, where cutting back is up to you · ${toFixed0(100 - share)}%",
    )
}

/** The Forecast card (renderForecast); null when there's nothing to forecast. */
data class ForecastCard(
    val figure: InsightFigure,
    val vsLastMonth: Change?,
    /** Spent, bills, usual pace: each a fraction of the total, for the stacked bar. */
    val parts: List<Double>,
    val legend: List<Pair<String, String>>,
    val nextName: String,
    val nextTotal: String,
    val nextNote: String,
)

private fun longMonth(month: String) = monthName(YearMonth.parse(month).monthValue)

fun forecastCard(f: Forecast, money: (Double) -> String): ForecastCard? {
    if (!(f.spent > 0) && f.billsTotal == 0.0 && !(f.perDay > 0)) return null
    val diff = f.total - f.lastMonthSpent
    val vs = if (f.lastMonthSpent > 0 && Math.abs(diff) >= 1) {
        Change("${money(Math.abs(diff))} ${if (diff > 0) "more" else "less"} than ${longMonth(addMonths(f.month, -1))} (${money(f.lastMonthSpent)})", diff > 0)
    } else null
    fun part(v: Double) = if (f.total > 0) v / f.total else 0.0
    val bills = if (f.billsDue.isEmpty()) "None left this month"
    else f.billsDue.take(3).joinToString(", ") { it.title } + if (f.billsDue.size > 3) " and ${f.billsDue.size - 3} more" else ""
    val oneOffs = if (f.oneOffs > 0) " and ${f.oneOffs} one-off big ${if (f.oneOffs == 1) "expense" else "expenses"}" else ""
    return ForecastCard(
        InsightFigure(money(f.total), "by the end of ${longMonth(f.month)}"),
        vs,
        listOf(part(f.spent), part(f.billsTotal), part(f.usualLeft)),
        listOf(
            "${money(f.spent)} spent so far" to "Up to today",
            "${money(f.billsTotal)} in bills still due" to bills,
            "${money(f.usualLeft)} at your usual pace" to
                "${money(f.perDay)} a day for the ${f.daysLeft} ${if (f.daysLeft == 1) "day" else "days"} left, leaving out bills$oneOffs",
        ),
        longMonth(f.next.month),
        "About ${money(f.next.total)}",
        "${money(f.next.fixed)} in ${f.next.bills} ${if (f.next.bills == 1) "bill" else "bills"} and EMIs, ${money(f.next.usual)} at your usual pace",
    )
}
