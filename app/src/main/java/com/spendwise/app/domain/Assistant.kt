package com.spendwise.app.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// The assistant: "Ask about your spending" and the AI coach as one conversation. A question is routed here, on
// the device, to a calculation, advice or a clarifying question. Every figure comes from [calculate]; the AI
// only writes advice around facts it's given (Coach.kt) and reads a question this parser can't (mergeAiSpec).

enum class FinMetric { TOTAL, COUNT, BIGGEST, LIST, DAILY_AVG, ACTIVE_DAY_AVG, MONTHLY_AVG, COMPARE, SAVED, TOP_CATEGORY, BIGGEST_INCREASE, BREAKDOWN }

/** A question as a structured query: dates are already resolved, so it stays right when saved and reopened. */
@Serializable
data class FinQuery(
    val metric: FinMetric,
    val type: AskType = AskType.EXPENSE,
    val text: String? = null,
    val tag: String? = null,
    val categories: List<String> = emptyList(),
    val from: String? = null,
    val to: String? = null,
    /** The other side of COMPARE and BIGGEST_INCREASE. */
    val otherFrom: String? = null,
    val otherTo: String? = null,
)

sealed interface AssistantRoute {
    /** [unsure]: the parser kept words it doesn't know, so the AI may read the question better. */
    data class Calculate(val query: FinQuery, val unsure: Boolean = false) : AssistantRoute
    data class Advise(val topic: CoachTopic, val from: String?, val to: String?, val categories: List<String>) : AssistantRoute
    data class Clarify(val text: String, val options: List<ClarifyOption>) : AssistantRoute
}

data class ClarifyOption(val label: String, val question: String)

/** What the conversation so far adds to a question: the last calculation, and whether advice came last. */
data class AssistantContext(val last: FinQuery? = null, val lastWasAdvice: Boolean = false, val lastTopic: CoachTopic? = null)

private fun Regex.hit(s: String) = containsMatchIn(s)

private val ADVICE = Regex("""\b(should|suggest\w*|advice|advise|tips?|recommend\w*|reduce|cut (back|down)?|lower|improve|analy[sz]e|analysis|habits?|too much|overspend\w*|how (can|do|could|to)|ways? to|plan|afford|budget|why|explain|will i|on track|next (week|month|year)|what if|is that (ok|okay|good|bad|normal))\b""")
private val FOLLOW_UP = Regex("""^\s*(and|what about|how about|same|also|only|just|then)\b""")
private val ALL_TIME = Regex("""\b(ever|overall|all time|all-time|all my|till now|until now|so far|till date|to date|since the start|in total)\b""")
private val AVERAGE = Regex("""\b(avg|average|averages|mean)\b""")
private val ACTIVE_DAYS = Regex("""\b(active days?|spending days?|days? (with|when|that|on which|i (spent|spend))|only days)\b""")
private val PER_DAY = Regex("""\b(daily|per day|a day|each day|every day)\b""")
private val PER_MONTH = Regex("""\b(monthly|per month|a month|each month|every month)\b""")
private val COMPARE = Regex("""\b(compare|compared|comparison|vs|versus|difference between)\b""")
private val INCREASE = Regex("""\b(increas\w*|grew|grown|gone up|went up|rose|risen)\b""")
private val SAVED = Regex("""\b(how much (did|have|am) (i|we)( been)? (save|saved|saving)|did i save|have i saved|i saved|what did i save|savings? rate|my saving\b)""")
private val TOP_CATEGORY = Regex("""\b((biggest|top|highest|largest|most) (\w+ )?categor\w*|categor\w* .*\b(most|biggest|highest|largest)|spen[dt] (the )?most( money)? on|most money on)\b""")
/** How holdings stand ("how are my investments doing", "mutual fund return"): the portfolio, not a sum of entries. */
private val INVEST_WORD = Regex("""\b(invest\w*|portfolio|holdings?|mutual funds?|stocks?|shares?|sips?|fds?|fixed deposits?|ppf|nps|epf|gold)\b""")
private val HOLDING_STATE = Regex("""\b(doing|perform\w*|returns?|gains?|growth|grow\w*|worth|value|diversif\w*|profit\w*|loss\w*|portfolio|holdings?|allocation|risk\w*)\b""")
private val BREAKDOWN = Regex("""\b(break\s?down|by category|per category|category wise|categorywise|where did (my|the) (money|spending)|where (did|does|is) .*\bgo(ing)?\b)""")

private fun metricIn(q: String, base: AskMeasure): FinMetric = when {
    COMPARE.hit(q) -> FinMetric.COMPARE
    INCREASE.hit(q) -> FinMetric.BIGGEST_INCREASE
    SAVED.hit(q) -> FinMetric.SAVED
    TOP_CATEGORY.hit(q) -> FinMetric.TOP_CATEGORY
    BREAKDOWN.hit(q) -> FinMetric.BREAKDOWN
    ACTIVE_DAYS.hit(q) -> FinMetric.ACTIVE_DAY_AVG
    // "daily" wins over "monthly" when both are said; an average that names neither is per calendar day
    PER_DAY.hit(q) -> FinMetric.DAILY_AVG
    AVERAGE.hit(q) && PER_MONTH.hit(q) -> FinMetric.MONTHLY_AVG
    PER_MONTH.hit(q) && !Regex("""\b(this|last|previous|past) month\b""").hit(q) -> FinMetric.MONTHLY_AVG
    AVERAGE.hit(q) -> FinMetric.DAILY_AVG
    base == AskMeasure.COUNT -> FinMetric.COUNT
    base == AskMeasure.BIGGEST -> FinMetric.BIGGEST
    base == AskMeasure.LIST -> FinMetric.LIST
    else -> FinMetric.TOTAL
}

private val NEEDS_PERIOD = setOf(FinMetric.TOTAL, FinMetric.DAILY_AVG, FinMetric.ACTIVE_DAY_AVG, FinMetric.MONTHLY_AVG, FinMetric.SAVED, FinMetric.TOP_CATEGORY, FinMetric.BREAKDOWN)

/** The period before: a whole month, a whole year, else the same number of days right before. */
internal fun priorPeriod(from: String, to: String, today: String): Pair<String, String> {
    val a = LocalDate.parse(from)
    val b = LocalDate.parse(to)
    if (a.dayOfMonth == 1 && YearMonth.from(a) == YearMonth.from(b) && (b == a.plusMonths(1).minusDays(1) || to == today)) {
        val p = a.minusMonths(1)
        return p.toString() to p.plusMonths(1).minusDays(1).toString()
    }
    if (a.dayOfYear == 1 && a.year == b.year && (b.dayOfYear == b.lengthOfYear() || to == today)) return "${a.year - 1}-01-01" to "${a.year - 1}-12-31"
    val days = ChronoUnit.DAYS.between(a, b)
    val end = a.minusDays(1)
    return end.minusDays(days).toString() to end.toString()
}

/** The period after, up to today: what a period that has ended is compared with. */
private fun nextPeriod(from: String, to: String, today: String): Pair<String, String> {
    val a = LocalDate.parse(from)
    val b = LocalDate.parse(to)
    val (s, e) = if (a.dayOfMonth == 1 && b == a.plusMonths(1).minusDays(1)) a.plusMonths(1).let { it to it.plusMonths(1).minusDays(1) }
    else if (a.dayOfYear == 1 && b.dayOfYear == b.lengthOfYear() && a.year == b.year) a.plusYears(1).let { it to it.plusYears(1).minusDays(1) }
    else b.plusDays(1).let { it to it.plusDays(ChronoUnit.DAYS.between(a, b)) }
    return s.toString() to minOf(e.toString(), today)
}

/** The two periods a comparison names: split at its joining word, each side with its own dates. */
private fun comparedPeriods(q: String, today: String): Pair<Pair<String, String>, Pair<String, String>>? {
    for (sep in listOf("vs", "versus", "with", "against", "and", "to")) {
        val parts = q.split(Regex("""\b$sep\b"""), limit = 2)
        if (parts.size < 2) continue
        val (a1, a2) = datesIn(parts[0], today)
        val (b1, b2) = datesIn(parts[1], today)
        if (a1 != null && a2 != null && b1 != null && b2 != null) return (a1 to a2) to (b1 to b2)
    }
    return null
}

private fun topicFor(q: String): CoachTopic = when {
    Regex("""\bbudget\b""").hit(q) -> CoachTopic.BUDGET
    Regex("""\b(debts?|owe|owed|loans?|lend|lent|borrow\w*)\b""").hit(q) -> CoachTopic.DEBT
    Regex("""\b(save|saving|savings|invest\w*|portfolio|sips?|mutual funds?|stocks?|holdings?)\b""").hit(q) -> CoachTopic.SAVINGS
    Regex("""\b(why|trend\w*|change\w*|compared?|before|on track|will i|next (week|month|year)|more than)\b""").hit(q) -> CoachTopic.TRENDS
    Regex("""\b(reduce|cut|lower|too much|overspend\w*|spend\w*|expens\w*|habits?)\b""").hit(q) -> CoachTopic.SPENDING
    else -> CoachTopic.OVERVIEW
}

/**
 * The question read on the device. Nothing here calls the AI. [found] says whether a free-text search matches any
 * entry: a question whose words match nothing ("how is my financial health") is for the AI, not a ₹0 search.
 */
fun routeQuestion(question: String, today: String, context: AssistantContext = AssistantContext(), found: (AskSpec) -> Boolean = { true }): AssistantRoute {
    val q = question.trim().lowercase().replace(Regex("""[?!]+$"""), "")
    impossibleDate(" $q ", today)?.let { bad ->
        return AssistantRoute.Clarify("\"$bad\" isn't a date on the calendar. Which dates do you mean?", STARTERS)
    }
    val spec = guessSpec(question, today)
    val advice = ADVICE.hit(q) && !SAVED.hit(q)
    if (advice || INVEST_WORD.hit(q) && HOLDING_STATE.hit(q)) {
        val (from, to) = datesIn(q.replace(Regex("""\bnext (week|month|year)\b"""), " "), today)
        return AssistantRoute.Advise(topicFor(q), from, to, spec.categories)
    }
    val named = spec.categories.isNotEmpty() || spec.text != null || spec.tag != null
    val hasDates = spec.from != null
    val metric = metricIn(q, spec.measure)
    val spoken = metric != FinMetric.TOTAL || hasDates || named || Regex("""\b(how much|total|spent|spend|spending|income)\b""").hit(q)
    val followUp = FOLLOW_UP.hit(q) || (context.last != null && q.split(Regex("""\s+""")).size <= 3 && metric == FinMetric.TOTAL && !Regex("""\b(how much|total)\b""").hit(q))

    // Talking on with the coach: anything that isn't plainly a sum stays advice
    if (context.lastWasAdvice && (followUp && !hasDates || !spoken || q.split(Regex("""\s+""")).size > 3 && !hasDates && !named && metric == FinMetric.TOTAL)) {
        return AssistantRoute.Advise(context.lastTopic ?: topicFor(q), null, null, spec.categories)
    }
    // Anything that isn't plainly a sum of the entries is a question for the AI
    if (!spoken && followUp && context.last == null) return AssistantRoute.Clarify("What should I look at? Name a category or a period.", STARTERS)
    if (!spoken) return AssistantRoute.Advise(topicFor(q), spec.from, spec.to, spec.categories)
    if (spec.text != null && spec.tag == null && !found(spec.copy(from = null, to = null))) {
        return AssistantRoute.Advise(topicFor(q), spec.from, spec.to, spec.categories)
    }

    val prev = context.last
    if (followUp && prev == null && !hasDates && !named && metric == FinMetric.TOTAL) {
        return AssistantRoute.Clarify("What should I look at? Name a category or a period.", STARTERS)
    }
    val merged = if (followUp && prev != null) merge(prev, spec, metric) else FinQuery(
        metric = metric, type = spec.type, text = spec.text.takeIf { metric !in NO_TEXT }, tag = spec.tag,
        categories = spec.categories, from = spec.from, to = spec.to,
    )

    if (merged.metric == FinMetric.COMPARE) return compareRoute(q, merged, today, prev, question)
    if (merged.metric == FinMetric.BIGGEST_INCREASE) return AssistantRoute.Calculate(increaseQuery(merged, today))
    val allTime = ALL_TIME.hit(q)
    if (merged.metric in NEEDS_PERIOD && merged.from == null && !allTime) {
        val asked = question.trim().trimEnd('?', '.', '!')
        return AssistantRoute.Clarify(
            "Which period do you mean?",
            listOf(ClarifyOption("This month", "$asked this month"), ClarifyOption("This year", "$asked this year"), ClarifyOption("All time", "$asked all time")),
        )
    }
    return AssistantRoute.Calculate(merged, unsure = (merged.text?.split(' ')?.size ?: 0) >= 2 && merged.categories.isEmpty() && merged.metric in BASIC)
}

private val NO_TEXT = setOf(FinMetric.SAVED, FinMetric.TOP_CATEGORY, FinMetric.BIGGEST_INCREASE, FinMetric.BREAKDOWN)
private val BASIC = setOf(FinMetric.TOTAL, FinMetric.COUNT, FinMetric.BIGGEST, FinMetric.LIST)
private val STARTERS = listOf(
    ClarifyOption("Spending this month", "How much did I spend this month?"),
    ClarifyOption("Compare months", "Compare this month with last month"),
    ClarifyOption("Where to cut back", "Where can I reduce my spending?"),
)

/** A follow-up: what it names replaces, what it leaves out carries over from the last answer. */
private fun merge(prev: FinQuery, spec: AskSpec, metric: FinMetric): FinQuery {
    val named = spec.categories.isNotEmpty() || spec.text != null || spec.tag != null
    return prev.copy(
        metric = if (metric != FinMetric.TOTAL) metric else prev.metric,
        type = if (named) spec.type else prev.type,
        text = if (named) spec.text else prev.text,
        tag = if (named) spec.tag else prev.tag,
        categories = if (named) spec.categories else prev.categories,
        from = spec.from ?: prev.from,
        to = if (spec.from != null) spec.to else prev.to,
        // a new period means the other side must be worked out again
        otherFrom = if (spec.from != null) null else prev.otherFrom,
        otherTo = if (spec.from != null) null else prev.otherTo,
    )
}

private fun compareRoute(q: String, query: FinQuery, today: String, prev: FinQuery?, question: String): AssistantRoute {
    comparedPeriods(q, today)?.let { (a, b) ->
        return AssistantRoute.Calculate(query.copy(from = a.first, to = a.second, otherFrom = b.first, otherTo = b.second))
    }
    val from = query.from
    val to = query.to
    val priorFrom = prev?.from
    val priorTo = prev?.to
    return when {
        // "compare with last month", after an answer about another period
        from != null && to != null && priorFrom != null && priorTo != null && (from != priorFrom || to != priorTo) ->
            AssistantRoute.Calculate(
                // It names only a period, so it's still about what the last answer was about
                (if (query.categories.isEmpty() && query.text == null && query.tag == null) query.copy(type = prev.type, text = prev.text, tag = prev.tag, categories = prev.categories) else query)
                    .copy(from = priorFrom, to = priorTo, otherFrom = from, otherTo = to),
            )
        query.otherFrom != null -> AssistantRoute.Calculate(query)
        from != null && to != null && to >= today -> priorPeriod(from, today, today).let { AssistantRoute.Calculate(query.copy(to = today, otherFrom = it.first, otherTo = it.second)) }
        // A period that has ended is the "before": it's compared with what came after it
        from != null && to != null -> nextPeriod(from, to, today).let { AssistantRoute.Calculate(query.copy(from = it.first, to = it.second, otherFrom = from, otherTo = to)) }
        else -> {
            val asked = question.trim().trimEnd('?', '.', '!')
            AssistantRoute.Clarify(
                "Which periods should I compare?",
                listOf(ClarifyOption("This month vs last month", "$asked this month with last month"), ClarifyOption("This year vs last year", "$asked this year with last year")),
            )
        }
    }
}

/** What grew: the month so far against the same days of the one before, as the Insights "What changed" card. */
private fun increaseQuery(q: FinQuery, todayS: String): FinQuery {
    val today = LocalDate.parse(todayS)
    val from = q.from ?: today.withDayOfMonth(1).toString()
    val to = minOf(q.to ?: todayS, todayS)
    val monthSoFar = from == today.withDayOfMonth(1).toString() && to == todayS
    val yearSoFar = from == "${today.year}-01-01" && to == todayS
    val range = if (monthSoFar) periodRange(InsightPeriod.MONTH, today) else if (yearSoFar) periodRange(InsightPeriod.YEAR, today) else null
    val (a, b) = (range as? Checked.Ok)?.value?.let { it.prevStart to it.prevEnd } ?: priorPeriod(from, to, todayS)
    return q.copy(from = from, to = to, otherFrom = a, otherTo = b)
}

/** The AI read a question the parser couldn't place. Dates the parser found stay: they don't depend on a model. */
fun mergeAiSpec(local: FinQuery, ai: AskSpec): FinQuery = local.copy(
    type = ai.type,
    text = ai.text,
    tag = ai.tag,
    categories = ai.categories,
    from = local.from ?: ai.from,
    to = if (local.from != null) local.to else ai.to,
    metric = if (local.metric != FinMetric.TOTAL) local.metric else when (ai.measure) {
        AskMeasure.COUNT -> FinMetric.COUNT
        AskMeasure.BIGGEST -> FinMetric.BIGGEST
        AskMeasure.LIST -> FinMetric.LIST
        AskMeasure.TOTAL -> FinMetric.TOTAL
    },
)

// ── The sums ──

data class FinFact(val label: String, val value: String)
data class FinRow(val title: String, val meta: String?, val amount: String)

/** One calculated answer. [up]: spending went up, drawn in the "over" colour; [changeText] says so in words too. */
data class FinCard(
    val title: String,
    val headline: String,
    val caption: String?,
    val changeText: String? = null,
    val up: Boolean = false,
    val facts: List<FinFact> = emptyList(),
    val rows: List<FinRow> = emptyList(),
    val basis: String,
    val history: AskHistory? = null,
    val moreRows: Int = 0,
)

private val YMD = Regex("""^\d{4}-\d{2}-\d{2}$""")
private fun validDate(s: String) = YMD.matches(s) && runCatching { LocalDate.parse(s) }.isSuccess

private fun FinQuery.spec(from: String?, to: String?) = AskSpec(type, text, tag, categories, from, to, AskMeasure.TOTAL)

/** Dates with no future in them, and nothing from a malformed date. Future-dated entries wait for their day. */
private fun rowsFor(q: FinQuery, from: String?, to: String?, expenses: List<Expense>, income: List<Income>, today: String): AskResult {
    val end = minOf(to ?: today, today)
    val r = runQuery(q.spec(from, end), expenses.filter { validDate(it.date) }, income.filter { validDate(it.date) })
    return r
}

private fun days(from: String, to: String) = ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)).toInt() + 1

/** Calendar months a range touches: a month in progress counts as one, and the basis line says so. */
internal fun monthsTouched(from: String, to: String): Int =
    (ChronoUnit.MONTHS.between(YearMonth.from(LocalDate.parse(from)), YearMonth.from(LocalDate.parse(to))).toInt() + 1).coerceAtLeast(1)

private fun fullDate(s: String): String = LocalDate.parse(s).let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${it.year}" }
private fun rangeText(from: String, to: String) = if (from == to) fullDate(from) else "${fullDate(from)} – ${fullDate(to)}"
private fun periodName(from: String?, to: String?, today: String) = periodText(from, to, today).removePrefix("in ").removePrefix("on ")
    .replaceFirstChar { it.uppercase() }

private fun subject(q: FinQuery): String = subjectText(q.spec(null, null)).ifEmpty { if (q.type == AskType.INCOME) "Income" else "All spending" }
private fun kindWord(q: FinQuery) = if (q.type == AskType.INCOME) "income" else "expense"
private fun count(n: Int, q: FinQuery) = if (q.type == AskType.INCOME) "$n income ${if (n == 1) "entry" else "entries"}" else "$n ${if (n == 1) "expense" else "expenses"}"

/** JS-free percentage: null when there's nothing before to compare with. */
internal fun percentChange(now: Double, before: Double): Double? = if (before > 0) (now - before) / before * 100 else null
private fun pctText(p: Double) = jsToFixed(kotlin.math.abs(p), if (kotlin.math.abs(p) < 10) 1 else 0) + "%"

private fun whatsCounted(q: FinQuery, refunds: Double, money: (Double) -> String): String {
    val base = when {
        q.type == AskType.INCOME -> "Added up from your recorded income."
        q.categories.isEmpty() && q.text == null && q.tag == null -> "Added up from your recorded expenses. Income and money put into savings or investments aren't counted."
        else -> "Based on your recorded ${subject(q)} expenses."
    }
    // Only where a refund could be mistaken for less spending: a total of everything, not one category or one entry
    val aboutEverything = q.categories.isEmpty() && q.text == null && q.tag == null && q.metric !in setOf(FinMetric.BIGGEST, FinMetric.COUNT, FinMetric.LIST)
    return if (refunds != 0.0 && q.type == AskType.EXPENSE && aboutEverything) "$base Refunds of ${money(refunds)} are recorded as income, so they aren't subtracted." else base
}

private val REFUND = Regex("""\b(refund|reversal|reversed|cashback)""", RegexOption.IGNORE_CASE)

/** The figures for a query, all from the entries given. No AI and no rounding until [money] shows them. */
fun calculate(q: FinQuery, expenses: List<Expense>, income: List<Income>, todayS: String, money: (Double) -> String): FinCard {
    val from = q.from
    if (from != null && from > todayS) {
        return FinCard(subject(q), money(0.0), "That date hasn't come yet, so nothing has been spent.", basis = "Only entries dated up to today are counted.")
    }
    return when (q.metric) {
        FinMetric.COMPARE -> compare(q, expenses, income, todayS, money)
        FinMetric.BIGGEST_INCREASE -> increase(q, expenses, income, todayS, money)
        FinMetric.SAVED -> saved(q, expenses, income, todayS, money)
        FinMetric.TOP_CATEGORY, FinMetric.BREAKDOWN -> breakdown(q, expenses, income, todayS, money)
        else -> plain(q, expenses, income, todayS, money)
    }
}

private fun plain(q: FinQuery, expenses: List<Expense>, income: List<Income>, today: String, money: (Double) -> String): FinCard {
    val r = rowsFor(q, q.from, q.to, expenses, income, today)
    val to = minOf(q.to ?: today, today)
    val period = periodName(q.from, if (q.to == null && q.from == null) null else to, today)
    val title = "${subject(q)} · $period"
    val refunds = income.filter { validDate(it.date) && (q.from == null || it.date >= q.from) && it.date <= to && REFUND.hit(it.source + " " + it.notes) }.sumOf { it.amount }
    val basis = whatsCounted(q, refunds, money)
    val hist = askHistory(q.spec(q.from, to).let { it.copy(categories = q.categories) })
    if (r.count == 0) {
        return FinCard(title, money(0.0), "No ${kindWord(q)}s found ${periodText(q.from, if (q.from == null) null else to, today)}.", basis = "Nothing matched in your recorded entries. Try a longer period or another word.")
    }
    val start = q.from ?: r.rows.minOf { it.date }
    val rowsOut = askRows(q.spec(q.from, to).copy(measure = if (q.metric == FinMetric.BIGGEST) AskMeasure.BIGGEST else AskMeasure.TOTAL), r)
        .map { FinRow(askName(it).ifEmpty { if (it is Entry.Earned) "Income" else it.kind }, listOf(it.kind, askDay(it.date, today)).filter { s -> s.isNotEmpty() }.joinToString(" · "), money(it.amount)) }
    val more = (r.count - rowsOut.size).coerceAtLeast(0)
    val span = FinFact("Period", rangeText(start, to))
    val calendarDays = days(start, to)
    val months = monthsTouched(start, to)
    val inProgress = to == today && LocalDate.parse(today) != LocalDate.parse(today).withDayOfMonth(LocalDate.parse(today).lengthOfMonth())
    val activeDays = r.rows.filter { it.amount > 0 }.map { it.date }.distinct().size
    return when (q.metric) {
        FinMetric.DAILY_AVG -> FinCard(
            "Average per day · ${subject(q)} · $period", money(r.total / calendarDays), "per calendar day",
            facts = listOf(FinFact("Total", money(r.total)), FinFact("Calendar days", "$calendarDays"), FinFact(if (q.type == AskType.INCOME) "Entries" else "Transactions", "${r.count}"), FinFact("On days with an entry", money(r.total / activeDays.coerceAtLeast(1))), span),
            basis = "${money(r.total)} ÷ $calendarDays calendar days, counting days with no entries. $basis", history = hist,
        )
        FinMetric.ACTIVE_DAY_AVG -> FinCard(
            "Average per active day · ${subject(q)} · $period", money(r.total / activeDays.coerceAtLeast(1)), "on days with at least one entry",
            facts = listOf(FinFact("Total", money(r.total)), FinFact("Days with an entry", "$activeDays of $calendarDays"), FinFact("Per calendar day", money(r.total / calendarDays)), span),
            basis = "${money(r.total)} ÷ $activeDays days with at least one entry. $basis", history = hist,
        )
        FinMetric.MONTHLY_AVG -> FinCard(
            "Average per month · ${subject(q)} · $period", money(r.total / months), "per month",
            facts = listOf(FinFact("Total", money(r.total)), FinFact("Months", "$months"), FinFact(if (q.type == AskType.INCOME) "Entries" else "Transactions", "${r.count}"), span),
            basis = "${money(r.total)} ÷ $months calendar ${if (months == 1) "month" else "months"}" +
                (if (inProgress && months > 1) ", counting the current month as a whole one" else "") + ". $basis",
            history = hist,
        )
        FinMetric.COUNT -> FinCard(title, "${r.count}", count(r.count, q), facts = listOf(FinFact("Total", money(kotlin.math.abs(r.total))), span), rows = rowsOut, moreRows = more, basis = basis, history = hist)
        FinMetric.BIGGEST -> {
            val b = r.biggest!!
            FinCard(title, money(b.amount), "${askName(b).ifEmpty { b.kind }}, ${askDay(b.date, today)}", facts = listOf(FinFact("Looked through", count(r.count, q)), span), rows = rowsOut, moreRows = more, basis = basis, history = hist)
        }
        else -> FinCard(
            title, money(r.total), count(r.count, q),
            facts = buildList {
                if (months > 1) add(FinFact("Monthly average", money(r.total / months)))
                add(FinFact("Highest", money(r.biggest!!.amount)))
                add(span)
            },
            rows = if (q.metric == FinMetric.LIST) rowsOut else emptyList(), moreRows = if (q.metric == FinMetric.LIST) more else 0,
            basis = basis, history = hist,
        )
    }
}

private fun compare(q: FinQuery, expenses: List<Expense>, income: List<Income>, today: String, money: (Double) -> String): FinCard {
    val aFrom = q.from!!
    val aTo = minOf(q.to ?: today, today)
    val bFrom = q.otherFrom!!
    val bTo = minOf(q.otherTo!!, today)
    val a = rowsFor(q, aFrom, aTo, expenses, income, today)
    val b = rowsFor(q, bFrom, bTo, expenses, income, today)
    val nameA = periodName(aFrom, aTo, today)
    val nameB = periodName(bFrom, bTo, today)
    val diff = a.total - b.total
    val p = percentChange(a.total, b.total)
    val word = if (q.type == AskType.INCOME) "income" else "spending"
    val change = when {
        p == null -> if (a.total > 0) "Nothing recorded in $nameB to compare with" else "Nothing recorded in either period"
        diff == 0.0 -> "The same in both periods"
        diff > 0 -> "Up ${pctText(p)} (${money(diff)} more)"
        else -> "Down ${pctText(p)} (${money(-diff)} less)"
    }
    val facts = mutableListOf(FinFact(nameA, money(a.total)), FinFact(nameB, money(b.total)))
    // A month or year in progress against a whole one flatters the numbers: show the fair comparison too
    val t = LocalDate.parse(today)
    val partial = aTo == today && bTo < aFrom && days(aFrom, aTo) < days(bFrom, bTo) && LocalDate.parse(bFrom).let { (it.dayOfMonth == 1 && aFrom.endsWith("-01")) }
    if (partial) {
        val sameEnd = LocalDate.parse(bFrom).plusDays(days(aFrom, aTo).toLong() - 1).toString()
        val same = rowsFor(q, bFrom, minOf(sameEnd, bTo), expenses, income, today)
        facts += FinFact("Same days of $nameB", money(same.total) + (percentChange(a.total, same.total)?.let { " (${if (it >= 0) "up" else "down"} ${pctText(it)})" } ?: ""))
    }
    return FinCard(
        "${subject(q)} · $nameA vs $nameB", money(a.total), "$word $nameA", change, up = diff > 0 && q.type != AskType.INCOME && p != null,
        facts = facts,
        basis = "${rangeText(aFrom, aTo)} against ${rangeText(bFrom, bTo)}." + if (t.toString() == aTo && partial) " $nameA isn't over yet." else "",
    )
}

private fun byCategoryOf(rows: List<Entry>): Map<String, Double> {
    val by = LinkedHashMap<String, Double>()
    rows.filterIsInstance<Entry.Spent>().forEach { val c = normalizeCategory(it.expense.category); by[c] = (by[c] ?: 0.0) + it.amount }
    return by
}

private fun increase(q: FinQuery, expenses: List<Expense>, income: List<Income>, today: String, money: (Double) -> String): FinCard {
    val aTo = minOf(q.to ?: today, today)
    val now = byCategoryOf(rowsFor(q, q.from, aTo, expenses, income, today).rows)
    val before = byCategoryOf(rowsFor(q, q.otherFrom, q.otherTo, expenses, income, today).rows)
    val nameA = periodName(q.from, aTo, today)
    val basis = "${rangeText(q.from!!, aTo)} against ${rangeText(q.otherFrom!!, q.otherTo!!)}, by category."
    val movers = (now.keys + before.keys).map { c -> Triple(c, now[c] ?: 0.0, before[c] ?: 0.0) }.filter { it.second > it.third }.sortedByDescending { it.second - it.third }
    val top = movers.firstOrNull() ?: return FinCard("Biggest increase · $nameA", money(0.0), "No category went up.", basis = basis)
    val p = percentChange(top.second, top.third)
    return FinCard(
        "Biggest increase · $nameA", money(top.second - top.third), "more on ${top.first}",
        p?.let { "Up ${pctText(it)}" } ?: "New: nothing before", up = true,
        facts = listOf(FinFact("Now", money(top.second)), FinFact("Before", money(top.third))),
        rows = movers.drop(1).take(3).map { FinRow(it.first, percentChange(it.second, it.third)?.let { c -> "Up ${pctText(c)}" } ?: "New", "+" + money(it.second - it.third)) },
        basis = basis,
    )
}

private fun saved(q: FinQuery, expenses: List<Expense>, income: List<Income>, today: String, money: (Double) -> String): FinCard {
    val to = minOf(q.to ?: today, today)
    val spent = runQuery(AskSpec(AskType.EXPENSE, null, null, emptyList(), q.from, to, AskMeasure.TOTAL), expenses.filter { validDate(it.date) }, emptyList())
    val earned = runQuery(AskSpec(AskType.INCOME, null, null, emptyList(), q.from, to, AskMeasure.TOTAL), emptyList(), income.filter { validDate(it.date) })
    val period = periodName(q.from, if (q.from == null) null else to, today)
    if (earned.count == 0) {
        return FinCard("Saved · $period", "—", "No income recorded, so savings can't be worked out.", facts = listOf(FinFact("Spent", money(spent.total))), basis = "Saved is income minus spending. Add your income to see it.")
    }
    val left = earned.total - spent.total
    return FinCard(
        "Saved · $period", money(kotlin.math.abs(left)), if (left >= 0) "saved, ${jsToFixed(left / earned.total * 100, 1)}% of income" else "more spent than earned",
        up = left < 0,
        facts = listOf(FinFact("Income", money(earned.total)), FinFact("Spent", money(spent.total))),
        basis = "Income minus spending, the same as Insights. Money put into savings or investments counts as saved.",
    )
}

private fun breakdown(q: FinQuery, expenses: List<Expense>, income: List<Income>, today: String, money: (Double) -> String): FinCard {
    val to = minOf(q.to ?: today, today)
    val r = runQuery(AskSpec(AskType.EXPENSE, null, null, emptyList(), q.from, to, AskMeasure.TOTAL), expenses.filter { validDate(it.date) }, emptyList())
    val period = periodName(q.from, if (q.from == null) null else to, today)
    val cats = byCategoryOf(r.rows).toList().sortedByDescending { it.second }
    val top = cats.firstOrNull() ?: return FinCard("Where it went · $period", money(0.0), "No expenses found.", basis = "Nothing matched in your recorded entries.")
    fun share(v: Double) = if (r.total > 0) jsToFixed(v / r.total * 100, 0) + "%" else "0%"
    val rows = cats.take(5).map { FinRow(it.first, "${share(it.second)} of spending", money(it.second)) }
    return if (q.metric == FinMetric.TOP_CATEGORY) FinCard(
        "Top category · $period", money(top.second), "${top.first}, ${share(top.second)} of spending",
        rows = rows.drop(1), moreRows = 0, facts = listOf(FinFact("All spending", money(r.total))), basis = "Added up by category. Money put into savings or investments isn't counted.",
    ) else FinCard(
        "Where it went · $period", money(r.total), count(r.count, q), rows = rows, basis = "Added up by category. Money put into savings or investments isn't counted.",
    )
}

/** The dates advice looks at: the ones asked about, else the last three months, the Insights "3 months" card. */
fun adviceRange(from: String?, to: String?, today: LocalDate): PeriodRange {
    val checked = if (from != null && to != null) periodRange(InsightPeriod.CUSTOM, today, from, minOf(to, today.toString()))
    else periodRange(InsightPeriod.QUARTER, today)
    return (checked as? Checked.Ok)?.value ?: (periodRange(InsightPeriod.QUARTER, today) as Checked.Ok).value
}

/** What the coach sees for a piece of advice: the period's facts, plus the monthly figures worked out here so it needn't divide. */
fun adviceFacts(s: InsightSummary, r: PeriodRange, money: (Double) -> String): String {
    val months = monthsTouched(r.start, minOf(r.end, LocalDate.now().toString()).let { if (it < r.start) r.start else it })
    val perMonth = s.categories.joinToString("; ") { "${it.category} ${money(it.amount / months)}" }
    val share = if (s.earned > 0) "; ${jsToFixed(s.spent / s.earned * 100, 0)}% of income" else ""
    return factsText(s, r, money) + "\nPER MONTH, over $months calendar ${if (months == 1) "month" else "months"}: all spending ${money(s.spent / months)}$share" +
        if (perMonth.isEmpty()) "" else "; $perMonth"
}

/** The calculated card that sits above a piece of advice, and stays when the AI can't answer. */
fun adviceQuery(route: AssistantRoute.Advise, range: PeriodRange): FinQuery =
    if (route.categories.isNotEmpty()) FinQuery(FinMetric.TOTAL, categories = route.categories, from = range.start, to = range.end)
    else FinQuery(FinMetric.BREAKDOWN, from = range.start, to = range.end)

/** [by]: the AI that wrote the answer. */
fun assistantAiNote(by: String) = (if (by.isEmpty()) "AI answer" else "Answered by $by") + ". It can be wrong."
const val ASSISTANT_NO_CONSENT = "Advice needs the AI, so here are just your figures. Your numbers are always worked out on this device."

val ASSISTANT_NUMBER_PROMPTS = listOf(
    "Average daily spending last month",
    "How much did I spend on food this month?",
    "Compare this month with last month",
    "Which category has increased the most?",
    "How much did I save this month?",
    "Biggest expense this year",
)
val ASSISTANT_ADVICE_PROMPTS = listOf(
    "Where can I reduce my spending?",
    "Am I spending too much on food?",
    "Suggest a monthly budget",
    "Analyze my spending habits",
)
