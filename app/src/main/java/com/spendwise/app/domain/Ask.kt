package com.spendwise.app.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// "Ask about your spending" (Insights), ported from ask-data.js (spec §1.7, §6.9): a question becomes a
// search spec, the spec runs over the user's own entries on the device, and the answer is worded here.
// Only the on-device reading (guessSpec) is ported; the AI's reading (askPrompt, readSpec) comes with AI.

enum class AskType { EXPENSE, INCOME, BOTH }
enum class AskMeasure { TOTAL, COUNT, BIGGEST, LIST }

data class AskSpec(
    val type: AskType,
    val text: String?,
    val tag: String?,
    val categories: List<String>,
    val from: String?,
    val to: String?,
    val measure: AskMeasure,
)

data class AskResult(val rows: List<Entry>, val total: Double, val count: Int, val biggest: Entry?)

data class AskAnswer(val headline: String, val sentence: String)

private val MONTH_NAMES = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
private val SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** dayText: "4 Sep", or "4 Sep 2025" outside the current year. */
fun askDay(s: String, today: String): String {
    val (y, m, d) = s.split("-").map { it.toInt() }
    return "$d ${SHORT_MONTHS[m - 1]}" + if (y.toString() != today.take(4)) " $y" else ""
}

// Words that name a category ("food" → Food & Dining)
private val CATEGORY_WORDS = listOf(
    Regex("""\b(food|dining|restaurants?|eating out|lunch|dinner|breakfast|swiggy|zomato)\b""") to "Food & Dining",
    Regex("""\b(groceries|grocery|vegetables|kirana|blinkit|zepto|bigbasket)\b""") to "Groceries",
    Regex("""\b(transport|petrol|fuel|diesel|cabs?|uber|ola|auto|metro|parking)\b""") to "Transport",
    Regex("""\b(travel|trips?|flights?|hotels?|trains?)\b""") to "Travel",
    Regex("""\b(shopping|clothes)\b""") to "Shopping",
    Regex("""\b(bills?|utilities|electricity|recharge|broadband|wifi)\b""") to "Bills & Utilities",
    Regex("""\b(rent|home)\b""") to "Home & Rent",
    Regex("""\b(health|medicines?|doctor|pharmacy|gym)\b""") to "Health",
    Regex("""\b(entertainment|movies?)\b""") to "Entertainment",
    Regex("""\b(education|courses?|books?|fees)\b""") to "Education",
    Regex("""\b(invest|investments?|invested|sips?|savings)\b""") to "Savings & Investments",
)
// Brands that also name a category: searched as text, which is narrower
private val BRANDS = Regex("""\b(swiggy|zomato|blinkit|zepto|bigbasket|uber|ola)\b""")
private val STOP = ("how much many what was were is are did do does i me my we our you on in at for from to of the a an all " +
    "spend spent send sent spending pay paid cost costs total sum show list tell expense expenses money rupees rs " +
    "this last past previous so far till until now date ever overall upto today yesterday week weeks month months year years day days " +
    "biggest largest most expensive count number times income earn earned earning received get got salary " +
    "order ordered orders buy bought purchase purchased purchases use used go went have had give gave put").split(" ").toSet()
// Smaller than STOP: words like "salary", "card" or "income" can be what an entry says
private val FILLER = ("how much many what was were is are did do does i me my we our you on in at for from to of the a an all " +
    "spend spent send sent spending pay paid total sum show list tell expense expenses money rupees rs " +
    "this last past previous so far till until now date ever overall upto today yesterday week weeks month months year years day days " +
    "order ordered buy bought purchase purchased use used go went have had").split(" ").toSet()

private fun Regex.test(s: String) = containsMatchIn(s)

/** [from, to] for the time a question names, or nulls. */
private fun datesIn(q: String, todayStr: String): Pair<String?, String?> {
    val t = LocalDate.parse(todayStr)
    val today = t.toString()
    if (Regex("""\btoday\b""").test(q)) return today to today
    if (Regex("""\byesterday\b""").test(q)) return t.minusDays(1).toString().let { it to it }
    val monday = t.minusDays((t.dayOfWeek.value - 1).toLong())
    if (Regex("""\bthis week\b""").test(q)) return monday.toString() to today
    if (Regex("""\b(last|previous|past) week\b""").test(q)) return monday.minusDays(7).toString() to monday.minusDays(1).toString()
    Regex("""\b(?:last|past) (\d{1,3}) days\b""").find(q)?.let { return t.minusDays(it.groupValues[1].toLong() - 1).toString() to today }
    val first = t.withDayOfMonth(1)
    if (Regex("""\bthis month\b""").test(q)) return first.toString() to today
    if (Regex("""\b(last|previous|past) month\b""").test(q)) return first.minusMonths(1).toString() to first.minusDays(1).toString()
    if (Regex("""\bthis year\b""").test(q)) return t.withDayOfYear(1).toString() to today
    if (Regex("""\b(last|previous|past) year\b""").test(q)) return "${t.year - 1}-01-01" to "${t.year - 1}-12-31"
    val year = Regex("""\b(20\d{2})\b""").find(q)?.groupValues?.get(1)?.toInt()
    val month = MONTH_NAMES.indexOfFirst { Regex("""\b($it|${it.take(3)})\b""").test(q) }
    if (month >= 0) {
        val m = t.monthValue - 1
        val yy = year ?: if (month <= m) t.year else t.year - 1 // the most recent past one
        val start = LocalDate.of(yy, month + 1, 1)
        return start.toString() to if (yy == t.year && month == m) today else start.plusMonths(1).minusDays(1).toString()
    }
    if (year != null) return "$year-01-01" to "$year-12-31"
    return null to null
}

private val TAG = Regex("""#([\p{L}\p{N}_-]+)""")

/** guessSpec: a spec for common questions, worked out without the AI. */
fun guessSpec(question: String, today: String): AskSpec {
    val q = " ${question.lowercase()} "
    val (from, to) = datesIn(q, today)
    val tag = TAG.find(q)?.groupValues?.get(1)
    val type = if (Regex("""\b(income|earn|earned|earning|received|got paid|salary|freelanc\w*)\b""").test(q)) AskType.INCOME else AskType.EXPENSE
    val measure = when {
        Regex("""\b(how many|count|number of|times)\b""").test(q) -> AskMeasure.COUNT
        Regex("""\b(biggest|largest|most expensive|highest)\b""").test(q) -> AskMeasure.BIGGEST
        Regex("""\b(show|list)\b""").test(q) -> AskMeasure.LIST
        else -> AskMeasure.TOTAL
    }
    val brand = BRANDS.find(q)?.groupValues?.get(1)
    val categories = if (type == AskType.EXPENSE && brand == null && tag == null) {
        CATEGORY_WORDS.filter { (re, _) -> re.test(q) }.map { it.second }.distinct()
    } else emptyList()
    // What's left after the known words: a shop or a word to look for
    var rest = q.replace(TAG, " ").replace(Regex("""[^\p{L}\p{N}\s]"""), " ").split(Regex("""\s+"""))
        .filter { w -> w.isNotEmpty() && w !in STOP && !w.all { it.isDigit() } && MONTH_NAMES.none { w == it || w == it.take(3) } }
    if (categories.isNotEmpty()) rest = rest.filter { w -> CATEGORY_WORDS.none { (re, _) -> re.test(" $w ") } }
    val text = brand ?: rest.takeIf { it.isNotEmpty() }?.joinToString(" ")
    return AskSpec(
        type,
        if (type == AskType.INCOME && text == null && Regex("""\bsalary\b""").test(q)) "salary" else text,
        tag, categories, from, to, measure,
    )
}

/** searchWords: filler dropped, and a plural matches its singular ("cards" finds "card"). */
fun searchWords(text: String?): List<String> = (text ?: "").lowercase().split(Regex("""[^\p{L}\p{N}#_-]+"""))
    .filter { it.isNotEmpty() && it !in FILLER }
    .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }

/** runQuery: the entries a spec picks, newest first, with the total, count and biggest. */
fun runQuery(spec: AskSpec, expenses: List<Expense>, income: List<Income>): AskResult {
    fun inDates(date: String) = (spec.from == null || date >= spec.from) && (spec.to == null || date <= spec.to)
    // Every word, each in any field. The separator keeps a word from spanning two fields.
    val words = searchWords(spec.text)
    fun has(fields: List<String>): Boolean {
        val hay = fields.filter { it.isNotEmpty() }.joinToString("\u0001").lowercase()
        return words.all { hay.contains(it) }
    }
    val wantInvest = SAVINGS_CATEGORY in spec.categories
    val rows = mutableListOf<Entry>()
    if (spec.type != AskType.INCOME) {
        for (e in expenses) {
            if (!inDates(e.date) || (!wantInvest && !isSpending(e.category))) continue
            if (spec.categories.isNotEmpty() && normalizeCategory(e.category) !in spec.categories) continue
            val tags = e.tags.map { it.lowercase() }
            if (spec.tag != null && spec.tag !in tags) continue
            if (words.isNotEmpty() && !has(listOf(e.description, e.notes, e.category, e.payment, e.cardName) + tags)) continue
            rows += Entry.Spent(e)
        }
    }
    if (spec.type != AskType.EXPENSE && spec.categories.isEmpty() && spec.tag == null) {
        for (i in income) {
            if (!inDates(i.date)) continue
            if (words.isNotEmpty() && !has(listOf(i.source, i.notes, i.paymentType, i.bank))) continue
            rows += Entry.Earned(i)
        }
    }
    rows.sortByDescending { it.date } // stable, as JS's sort
    val total = rows.sumOf { r -> if (r is Entry.Earned) r.amount else if (spec.type == AskType.BOTH) -r.amount else r.amount }
    val biggest = rows.fold(null as Entry?) { a, r -> if (a == null || r.amount > a.amount) r else a }
    return AskResult(rows, total, rows.size, biggest)
}

private fun lastOfMonth(y: Int, m: Int) = LocalDate.of(y, m, 1).plusMonths(1).minusDays(1).toString()

/** periodText: "in September", "in October so far", "on 24 Oct", "from 1 Sep to 15 Sep", "in all your entries". */
fun periodText(from: String?, to: String?, today: String): String {
    if (from == null && to == null) return "in all your entries"
    val thisYear = today.take(4)
    if (from != null && from == to) return "on ${askDay(from, today)}"
    if (from == "$thisYear-01-01" && to == today) return "this year"
    if (from != null && to != null && from.endsWith("-01-01") && to.endsWith("-12-31") && from.take(4) == to.take(4)) return "in ${from.take(4)}"
    if (from != null && to != null && from.endsWith("-01") && from.take(7) == to.take(7)) {
        val (y, m) = from.split("-").map { it.toInt() }
        val last = lastOfMonth(y, m)
        if (to == last || to == today) {
            val name = java.time.Month.of(m).getDisplayName(TextStyle.FULL, Locale.US) + if (y.toString() != thisYear) " $y" else ""
            return if (to == last) "in $name" else "in $name so far"
        }
    }
    if (from != null && to == null) return "since ${askDay(from, today)}"
    if (from == null && to != null) return "up to ${askDay(to, today)}"
    return "from ${askDay(from!!, today)} to ${askDay(to!!, today)}"
}

private val WORD_START = Regex("""\b\p{L}""")

/** subjectText: what was searched for, in a few words: "Amazon", "#goa2026", "Food & Dining". */
fun subjectText(spec: AskSpec): String {
    if (spec.tag != null) return "#${spec.tag}"
    if (spec.text != null) {
        val shown = spec.text.split(Regex("""\s+""")).filter { it.isNotEmpty() && it.lowercase() !in FILLER }.joinToString(" ")
        if (shown.isNotEmpty()) return WORD_START.replace(shown) { it.value.uppercase() }
    }
    return spec.categories.joinToString(", ")
}

/** The name an answer gives a row: the source, else the description unless it's "-", else the category. */
fun askName(r: Entry): String = when (r) {
    is Entry.Earned -> r.income.source
    is Entry.Spent -> r.expense.description.takeIf { it.isNotEmpty() && it != "-" } ?: r.expense.category
}

/** answerFor: the answer card's headline and sentence. */
fun answerFor(spec: AskSpec, result: AskResult, money: (Double) -> String, today: String): AskAnswer {
    val (one, many) = when (spec.type) {
        AskType.INCOME -> "income entry" to "income entries"
        AskType.BOTH -> "entry" to "entries"
        AskType.EXPENSE -> "expense" to "expenses"
    }
    val count = "${result.count} ${if (result.count == 1) one else many}"
    val subject = subjectText(spec)
    val on = if (subject.isEmpty()) "" else if (spec.type == AskType.INCOME) " from $subject" else " on $subject"
    val whenText = periodText(spec.from, spec.to, today)
    if (result.count == 0) return AskAnswer(if (spec.measure == AskMeasure.COUNT) "0" else money(0.0), "Nothing$on $whenText.")
    if (spec.measure == AskMeasure.COUNT) return AskAnswer(result.count.toString(), "$count$on $whenText, ${money(kotlin.math.abs(result.total))} in all.")
    if (spec.measure == AskMeasure.BIGGEST) {
        val b = result.biggest!!
        return AskAnswer(money(b.amount), "Biggest$on $whenText: ${askName(b).ifEmpty { "Entry" }}, ${askDay(b.date, today)}.")
    }
    if (spec.type == AskType.BOTH) {
        val lead = if (result.total >= 0) "More came in than went out" else "More went out than came in"
        return AskAnswer(money(kotlin.math.abs(result.total)), "$lead$on $whenText · $count.")
    }
    return AskAnswer(money(result.total), "${if (spec.type == AskType.INCOME) "Received" else "Spent"}$on $whenText · $count.")
}

/** searchedText: "Amazon · 1 Sep – 30 Sep · expenses", so a misread question is easy to spot. */
fun searchedText(spec: AskSpec, today: String): String {
    val dates = if (spec.from != null || spec.to != null) {
        "${spec.from?.let { askDay(it, today) } ?: "The start"} – ${spec.to?.let { askDay(it, today) } ?: "today"}"
    } else "All time"
    val kind = when (spec.type) { AskType.INCOME -> "income"; AskType.BOTH -> "expenses and income"; AskType.EXPENSE -> "expenses" }
    return listOf(subjectText(spec).ifEmpty { "Everything" }, dates, kind).joinToString(" · ")
}

/** The rows shown under an answer: newest first, or largest first for "biggest"; at most 5. */
fun askRows(spec: AskSpec, result: AskResult): List<Entry> =
    (if (spec.measure == AskMeasure.BIGGEST) result.rows.sortedByDescending { it.amount } else result.rows).take(5)

/** historyLink's filters: the same search in History, one kind at a time; null for "both". */
data class AskHistory(val income: Boolean, val q: String?, val from: String?, val to: String?, val cats: List<String>)

fun askHistory(spec: AskSpec): AskHistory? {
    if (spec.type == AskType.BOTH) return null
    val q = if (spec.tag != null) "#${spec.tag}" else searchWords(spec.text).joinToString(" ")
    return AskHistory(spec.type == AskType.INCOME, q.ifEmpty { null }, spec.from, spec.to, spec.categories)
}

const val ASK_MAX = 200
val ASK_EXAMPLES = listOf("Amazon last month", "Food this week", "Biggest expense this year", "Salary this month")
