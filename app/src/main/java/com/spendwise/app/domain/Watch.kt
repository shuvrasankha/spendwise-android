package com.spendwise.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// Checks run on the device over the user's own entries: unusual expenses, likely subscriptions, a goal's
// plan against what's really being saved, and the open debts as the AI may see them (amounts, no names).

private val YMD = Regex("""^\d{4}-\d{2}-\d{2}$""")
private fun dated(e: Expense) = YMD.matches(e.date) && runCatching { LocalDate.parse(e.date) }.isSuccess

private fun median(xs: List<Double>): Double = xs.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }

// ── Unusual expenses ──

enum class UnusualKind { HIGH, DUPLICATE }

/** One expense worth a second look. [usual] is the category's typical amount for HIGH. */
data class Unusual(val expense: Expense, val kind: UnusualKind, val usual: Double = 0.0)

/** At least this many earlier entries in a category before "usual" means anything. */
internal const val UNUSUAL_MIN_HISTORY = 4
/** This many times the usual amount. */
internal const val UNUSUAL_FACTOR = 3.0

/**
 * Expenses in [from]..[to] that stand out: [UNUSUAL_FACTOR] times the median of the same category over the
 * 90 days before, or the same amount, category and description twice on one day. Repeating bills are
 * expected, so they never count. Biggest first, at most [limit].
 */
fun unusualExpenses(expenses: List<Expense>, from: String, to: String, limit: Int = 3): List<Unusual> {
    val spending = expenses.filter { dated(it) && isSpending(it.category) && it.repeat == null && it.amount > 0 }
    val inRange = spending.filter { it.date in from..to }
    val out = mutableListOf<Unusual>()
    val seen = mutableSetOf<String>()
    for (e in inRange.sortedByDescending { it.amount }) {
        val cat = normalizeCategory(e.category)
        val start = LocalDate.parse(e.date).minusDays(90).toString()
        val before = spending.filter { it.id != e.id && normalizeCategory(it.category) == cat && it.date >= start && it.date < e.date }.map { it.amount }
        if (before.size >= UNUSUAL_MIN_HISTORY) {
            val usual = median(before)
            if (usual > 0 && e.amount >= usual * UNUSUAL_FACTOR) { out += Unusual(e, UnusualKind.HIGH, usual); seen += e.id; continue }
        }
        val twin = inRange.firstOrNull {
            it.id != e.id && it.id !in seen && it.date == e.date && it.amount == e.amount && normalizeCategory(it.category) == cat &&
                it.description.trim().equals(e.description.trim(), ignoreCase = true)
        }
        if (twin != null) { out += Unusual(e, UnusualKind.DUPLICATE); seen += e.id; seen += twin.id }
    }
    return out.take(limit)
}

/** The line under an unusual expense, in words: colour never says it alone. */
fun Unusual.why(money: (Double) -> String): String = when (kind) {
    UnusualKind.HIGH -> "${jsToFixed(expense.amount / usual, 0)}× your usual ${money(usual)} on ${normalizeCategory(expense.category)}"
    UnusualKind.DUPLICATE -> "Same amount twice on this day: a duplicate?"
}

/** What "Ask about it" sends: the figures, never the description. */
fun Unusual.question(money: (Double) -> String): String = when (kind) {
    UnusualKind.HIGH -> "Why might a ${money(expense.amount)} ${normalizeCategory(expense.category)} expense on ${expense.date} be ${jsToFixed(expense.amount / usual, 0)} times my usual ${money(usual)}, and should I worry?"
    UnusualKind.DUPLICATE -> "I have two ${money(expense.amount)} ${normalizeCategory(expense.category)} expenses on ${expense.date}. How do I check if I was charged twice?"
}

// ── Likely subscriptions ──

/** A charge that comes back every month but isn't marked as repeating. [latest] is the one to open. */
data class Subscription(val name: String, val monthly: Double, val months: Int, val latest: Expense)

private fun key(description: String) = description.lowercase().replace(Regex("""[^a-z ]"""), " ").replace(Regex("""\s+"""), " ").trim()

/**
 * Descriptions seen in at least 3 different months of the last 6, each time within 15% of their median,
 * and not already a repeating bill. Monthly amount biggest first.
 */
fun likelySubscriptions(expenses: List<Expense>, today: String): List<Subscription> {
    val since = YearMonth.parse(today.take(7)).minusMonths(5).atDay(1).toString()
    val repeating = expenses.filter { it.repeat != null }.map { key(it.description) }.toSet()
    return expenses.asSequence()
        .filter { dated(it) && it.date in since..today && isSpending(it.category) && it.repeat == null && it.amount > 0 }
        .groupBy { key(it.description) }
        .filterKeys { it.length >= 3 && it !in repeating }
        .mapNotNull { (_, list) ->
            val months = list.map { it.date.take(7) }.toSet()
            if (months.size < 3) return@mapNotNull null
            val m = median(list.map { it.amount })
            if (list.any { kotlin.math.abs(it.amount - m) > m * 0.15 }) return@mapNotNull null
            val latest = list.maxBy { it.date }
            Subscription(latest.description.trim(), m, months.size, latest)
        }
        .sortedByDescending { it.monthly }
        .toList()
}

// ── Goal plan ──

/**
 * Income less spending, per month, over the last 3 whole months; null without income in them. Money put into
 * savings or investments isn't spending, so it counts as saved, as on Insights.
 */
fun averageMonthlySaving(expenses: List<Expense>, income: List<Income>, today: String): Double? {
    val first = YearMonth.parse(today.take(7)).minusMonths(3)
    val from = first.atDay(1).toString()
    val to = first.plusMonths(2).atEndOfMonth().toString()
    val earned = income.filter { it.date in from..to }.sumOf { it.amount }
    if (earned <= 0) return null
    val spent = expenses.filter { dated(it) && it.date in from..to && isSpending(it.category) }.sumOf { it.amount }
    return (earned - spent) / 3
}

/** The goal sheet's plan line: what it takes a month, and whether what's saved now covers it. Null until it can say. */
fun goalPlanText(target: Double, saved: Double, by: String, today: String, monthlySaving: Double?, money: (Double) -> String): String? {
    if (target <= 0) return null
    val pace = goalPace(Goal("", "", target, by, saved), today)
    if (pace.left == 0.0) return "Already saved in full."
    if (pace.months <= 0) return "That month has passed. Pick a later one."
    val month = YearMonth.parse(by).let { "${it.month.getDisplayName(TextStyle.FULL, Locale.US)} ${it.year}" }
    val need = "${money(pace.monthly)} a month until $month."
    val have = monthlySaving ?: return "$need Add your income to see if you're saving enough."
    return when {
        have <= 0 -> "$need You've spent more than you earned over the last 3 months, so this needs cuts first."
        have >= pace.monthly -> "$need You've saved about ${money(have)} a month lately, so this fits."
        else -> "$need You've saved about ${money(have)} a month lately: ${money(pace.monthly - have)} short each month."
    }
}

// ── Debts for the AI ──

/** Open debts as the AI may see them: what's left, which way, since when. No names, emails or notes. */
fun debtFacts(debts: List<Debt>, today: String, money: (Double) -> String): String {
    val open = debts.filter { !it.settled && !it.shared && it.left() > 0 }
    if (open.isEmpty()) return "OPEN DEBTS: none"
    fun line(d: Debt): String {
        val days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(d.date), LocalDate.parse(today)) }.getOrNull()
        val paid = if (d.paid.isNotEmpty()) ", ${money(debtPaid(d.paid))} paid back so far" else ""
        return "${money(d.left())} left of ${money(d.amount)}${days?.let { ", $it days old" } ?: ""}$paid"
    }
    val owe = open.filter { it.type == I_OWE }.sortedByDescending { it.left() }
    val owed = open.filter { it.type == THEY_OWE }.sortedByDescending { it.left() }
    return listOfNotNull(
        owe.takeIf { it.isNotEmpty() }?.joinToString("; ", "USER OWES (one person each, no interest recorded): ") { line(it) },
        owed.takeIf { it.isNotEmpty() }?.joinToString("; ", "OWED TO USER: ") { line(it) },
    ).joinToString("\n")
}

/** Debts › "Make a payoff plan". The Assistant routes "plan … debts" to debt advice. */
const val DEBT_PLAN_QUESTION = "Make a plan for my debts: what to pay back first, how much a month, and what to follow up"
