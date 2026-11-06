package com.spendwise.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.ceil
import kotlin.math.floor

// Ported from budget-data.js (spec §6.8): "Suggest from my income". The last three months are added up on
// the device; the AI gets only those numbers, never an entry, description or name. Its answer is checked
// against the same numbers, and suggestFromFacts gives a plan worked out on the device when it can't help.
// Android goes further than the web (spec §6.8): limits come from a typical month, not the plain average,
// and an AI plan that misses the saving aim is not used. The AI also gets the month, its festivals and
// holidays (HolidayFeed), the same month last year, the user's goals by name, and their note of plans.

private const val LOOKBACK = 3
private const val SAVE_TARGET = 0.2
/** A category's average must top its typical month by this much for the difference to count as one-off. */
private const val ONE_OFF_MIN = 100.0
private const val ONE_OFF_SHARE = 0.1
/** An AI plan may leave this share of its total outside the categories, and go this far over a typical month. */
private const val UNASSIGNED_SHARE = 0.1
private const val OVER_TYPICAL = 1.1
/** What a budget limits: every category but savings. */
val BUDGET_CATEGORIES = EXPENSE_CATEGORIES - SAVINGS_CATEGORY
const val BUDGET_AI_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"

data class BudgetFacts(
    val months: Int, val income: Double, val spent: Double, val invested: Double,
    val cats: Map<String, Double>, val fixed: Map<String, Double>, val goalsMonthly: Double,
    /** A month's worth of spending above the typical month, by category. */
    val oneOffs: Map<String, Double> = emptyMap(),
    /** The month being budgeted, "YYYY-MM". */
    val month: String = "",
    /** Spending by category in the same month a year earlier. */
    val lastYear: Map<String, Double> = emptyMap(),
    /** Goals not yet reached, for the AI to plan around: a goal due this month is likely a purchase. */
    val goals: List<Goal> = emptyList(),
)

/** A goal due this month, or the user's note, asks for more than a typical month (see aiPlanHolds). */
fun hasPlans(f: BudgetFacts, plans: String) = plans.isNotEmpty() || f.goals.any { it.by == f.month }

/** What the user says is coming up this month ("Diwali shopping, Goa trip"), as sent to the AI. */
const val PLANS_MAX = 200
fun cleanPlans(text: String) = text.replace(Regex("\\s+"), " ").trim().take(PLANS_MAX)

data class BudgetSuggestion(val total: Double, val cats: Map<String, Double>, val tip: String, val saves: Double)

/** Math.round: halves go up. */
private fun jsRound(n: Double, step: Double = 100.0) = floor(n / step + 0.5) * step
private fun roundUp(n: Double, step: Double = 100.0) = ceil(n / step) * step

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/**
 * The last three whole months with any entries; null when none of them has income. Income and investing are
 * monthly averages. Spending is a typical month: the median month per category, and at least its repeating bills.
 */
fun budgetFacts(expenses: List<Expense>, income: List<Income>, goals: List<Goal>, today: String): BudgetFacts? {
    val month = today.take(7)
    val months = (1..LOOKBACK).map { addMonths(month, -it) }
    val active = months.filter { m -> expenses.any { it.date.take(7) == m } || income.any { it.date.take(7) == m } }
    if (active.none { m -> income.any { it.date.take(7) == m } }) return null
    val n = active.size
    val recent = expenses.filter { it.date.take(7) in active }
    val spending = recent.filter { isSpending(it.category) }
    val cats = LinkedHashMap<String, Double>()
    val fixed = LinkedHashMap<String, Double>()
    val oneOffs = LinkedHashMap<String, Double>()
    spending.groupBy { normalizeCategory(it.category) }.forEach { (c, list) ->
        // Each repeating bill counts once, at its latest amount, however many payments fell in these months
        val bills = list.filter { it.repeat == "monthly" || (it.emiTotal ?: 0) > 0 }
            .groupBy { it.description.trim().lowercase() }.values
            .sumOf { same -> same.maxBy { it.date }.amount }
        val typical = maxOf(median(active.map { m -> list.filter { it.date.take(7) == m }.sumOf { it.amount } }), bills)
        val average = list.sumOf { it.amount } / n
        if (typical > 0) cats[c] = typical
        if (bills != 0.0) fixed[c] = bills
        if (average - typical >= maxOf(ONE_OFF_MIN, ONE_OFF_SHARE * typical)) oneOffs[c] = average - typical
    }
    val goalsMonthly = goals.sumOf { g ->
        if (!(g.target > 0) || !Regex("""^\d{4}-\d{2}$""").matches(g.by)) 0.0
        else goalPace(g, today).let { if (it.level == GoalLevel.ON) it.monthly else 0.0 }
    }
    return BudgetFacts(
        months = n,
        income = income.filter { it.date.take(7) in active }.sumOf { it.amount } / n,
        spent = cats.values.sum(),
        invested = recent.filter { !isSpending(it.category) }.sumOf { it.amount } / n,
        cats = cats, fixed = fixed, goalsMonthly = goalsMonthly, oneOffs = oneOffs,
        month = month,
        goals = goals.filter { it.target > 0 && it.saved < it.target && Regex("""^\d{4}-\d{2}$""").matches(it.by) && it.by >= month },
        lastYear = expenses.filter { it.date.take(7) == addMonths(month, -12) && isSpending(it.category) }
            .groupBy { normalizeCategory(it.category) }.mapValues { (_, l) -> l.sumOf { it.amount } }.filterValues { it > 0 },
    )
}

/** The numbers the AI sees, as plain lines (categories are the app's own fixed names). */
fun factsLines(f: BudgetFacts, symbol: String): String {
    fun m(v: Double) = "$symbol${floor(v + 0.5).toLong()}"
    val byCat = BUDGET_CATEGORIES.filter { (f.cats[it] ?: 0.0) > 0 }.map { c ->
        "- $c: ${m(f.cats.getValue(c))}" + (f.fixed[c]?.takeIf { it != 0.0 }?.let { " (of which ${m(it)} repeating bills/EMIs)" } ?: "")
    }
    val oneOffs = BUDGET_CATEGORIES.filter { it in f.oneOffs }.map { "- $it: ${m(f.oneOffs.getValue(it))} a month" }
    val goals = f.goals.map { g ->
        val by = runCatching { java.time.YearMonth.parse(g.by).let { "${it.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)} ${it.year}" } }.getOrDefault(g.by)
        "- ${g.name}: ${m(g.target)} by $by, ${m(g.saved)} saved" + if (g.by == f.month) " (due this month)" else ""
    }
    val lastYear = BUDGET_CATEGORIES.filter { it in f.lastYear }.map { "- $it: ${m(f.lastYear.getValue(it))}" }
    val monthName = runCatching { java.time.YearMonth.parse(f.month).let { "${it.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${it.year}" } }.getOrNull()
    return (listOfNotNull(
        monthName?.let { "Budget for: $it" },
        "Over the last ${f.months} ${if (f.months == 1) "month" else "months"}:",
        "Income: ${m(f.income)} a month on average",
        "Spending: ${m(f.spent)} in a typical month",
        "Invested: ${m(f.invested)}",
        if (f.goalsMonthly != 0.0) "Savings goals need: ${m(f.goalsMonthly)} a month" else "Savings goals: none",
        "Spending by category, in a typical month:",
    ) + byCat.ifEmpty { listOf("- none") } + (if (oneOffs.isEmpty()) emptyList() else listOf("One-off spending on top of that, averaged:") + oneOffs) +
        (if (lastYear.isEmpty()) emptyList() else listOf("The same month last year, by category:") + lastYear) +
        (if (goals.isEmpty()) emptyList() else listOf("Savings goals, in the user's words:") + goals)).joinToString("\n")
}

/** budgetPrompt: (system, user). [plans] is the user's own note, already cleaned; [holidays] come from HolidayFeed. */
fun budgetPrompt(f: BudgetFacts, currency: Currency, plans: String = "", holidays: List<String> = emptyList()): Pair<String, String> = """You set a monthly spending budget for a SpendWise user from their own numbers. Amounts are in ${currency.code} (${currency.symbol}).
Rules:
- The total must leave money over from income: aim to keep at least 20% of income, more if savings goals need it, but never cut a category below its repeating bills/EMIs.
- Cut flexible categories (food, shopping, entertainment, travel) before essentials (groceries, bills, rent, health, education).
- Only use categories from this list: ${BUDGET_CATEGORIES.joinToString(", ")}. Leave out categories with no spending, unless the same month last year or the user's plans need them.
- Budget for a typical month: don't add one-off spending to the limits, and keep each category within 10% of its typical month or of the same month last year, whichever is higher.
- Think about the month being budgeted: the festivals and holidays listed (when there's no list, those usual then where the user lives; the currency shows the region), weddings or sale seasons, and how the same month went last year. Make room for them by moving money between flexible categories.
- If the user lists plans for the month, or a goal is due this month (likely a purchase), raise only the categories those need, enough to cover them, and keep the rest tight. Goals due later need saving, not spending.
- Give every category with spending a limit, adding up to the total.
- Whole numbers only. Use only the numbers given; never invent income or spending.
Reply with ONLY one JSON object, no other text:
{"total":<number>,"cats":{"<category>":<number>},"tip":"<one sentence, under 25 words, naming any festival, season or plan you allowed for, and where the saving comes from>"}""" to
    factsLines(f, currency.symbol) +
    (if (holidays.isEmpty()) "" else "\nFestivals and holidays this month, from the public calendar:\n" + holidays.joinToString("\n") { "- $it" }) +
    (if (plans.isEmpty()) "" else "\nThe user's plans for this month, in their words: $plans")

/** A plan worked out on the device: spending trimmed toward keeping a fifth of income, from flexible spending only. */
fun suggestFromFacts(f: BudgetFacts): BudgetSuggestion? {
    val keep = maxOf(SAVE_TARGET * f.income, f.goalsMonthly)
    val fixedTotal = f.fixed.values.sum()
    val target = maxOf(minOf(f.spent, f.income - keep), fixedTotal)
    val flexible = f.spent - fixedTotal
    val scale = if (flexible > 0) maxOf(target - fixedTotal, 0.0) / flexible else 1.0
    val cats = BUDGET_CATEGORIES.mapNotNull { c ->
        val spent = f.cats[c] ?: 0.0
        if (!(spent > 0)) return@mapNotNull null
        val fixed = f.fixed[c] ?: 0.0
        // the trimmed part rounds down, so the categories fit the total
        val v = roundUp(fixed) + floor((spent - fixed) * scale / 100) * 100
        if (v > 0) c to v else null
    }
    return checkSuggestion(target, cats, null, f)
}

/**
 * Makes any suggestion safe to show: known categories only, whole hundreds, no category below its bills,
 * a total that covers the categories and stays within income. null when nothing is usable.
 */
fun checkSuggestion(rawTotal: Double, rawCats: List<Pair<String, Double>>, rawTip: String?, f: BudgetFacts, allowNew: Boolean = false): BudgetSuggestion? {
    val cats = LinkedHashMap<String, Double>()
    rawCats.forEach { (name, n) ->
        val c = normalizeCategory(name)
        // A category with no recent spending needs last year's same month, or the user's plans, behind it
        if (c !in BUDGET_CATEGORIES || !(n > 0) || !((f.cats[c] ?: 0.0) > 0 || c in f.lastYear || allowNew)) return@forEach
        cats[c] = maxOf(roundUp(n), roundUp(f.fixed[c] ?: 0.0))
    }
    val catSum = cats.values.sum()
    var total = if (rawTotal > 0) rawTotal else catSum
    total = maxOf(jsRound(total), catSum)
    if (!(total > 0)) return null
    if (total > f.income) total = maxOf(floor(f.income / 100) * 100, catSum) // over income only when the bills are
    val tip = rawTip?.replace(Regex("\\s+"), " ")?.trim()?.take(160).orEmpty().let {
        if (it.isEmpty() || it.last() in ".!?") it else "$it."
    }
    return BudgetSuggestion(total, cats, tip, f.income - total)
}

/**
 * Whether an AI plan keeps what it should: limits that add up and every bill covered, plus, without the user's
 * plans, the saving aim met and nothing above a typical month or the same month last year. With plans (a trip,
 * a big buy) the user has asked for more, so a category may go higher and the plan only has to stay within income.
 */
fun aiPlanHolds(s: BudgetSuggestion, f: BudgetFacts, hasPlans: Boolean = false): Boolean {
    val keep = if (hasPlans) 0.0 else maxOf(SAVE_TARGET * f.income, f.goalsMonthly)
    val ceiling = maxOf(f.income - keep, f.fixed.values.sum()) + 100 * (s.cats.size + 1) // room for rounding up to hundreds
    return s.cats.isNotEmpty() && s.total <= ceiling &&
        s.total - s.cats.values.sum() <= UNASSIGNED_SHARE * s.total &&
        f.fixed.keys.all { it !in BUDGET_CATEGORIES || it in s.cats } &&
        (hasPlans || s.cats.all { (c, v) -> v <= roundUp(maxOf(f.cats[c] ?: 0.0, f.lastYear[c] ?: 0.0) * OVER_TYPICAL) })
}

/** The month's AI plan as stored on the device; checkReply reads it back. */
fun planJson(s: BudgetSuggestion): String = buildJsonObject {
    put("total", s.total)
    putJsonObject("cats") { s.cats.forEach { (c, v) -> put(c, v) } }
    put("tip", s.tip)
}.toString()

/** The AI's answer, checked; Number() rules for its values. */
fun checkReply(raw: JsonObject?, f: BudgetFacts, allowNew: Boolean = false): BudgetSuggestion? {
    if (raw == null) return null
    val cats = (raw["cats"] as? JsonObject).orEmpty().map { (k, v) -> k to jsNumberOf(v) }
    return checkSuggestion(jsNumberOf(raw["total"]), cats, (raw["tip"] as? JsonPrimitive)?.takeIf { it.isString }?.content, f, allowNew)
}

/** readReply: the JSON object in a reply that may wrap it in a code block or add words around it. */
fun readReply(text: String?): JsonObject? {
    val m = Regex("""\{[\s\S]*\}""").find(text.orEmpty()) ?: return null
    return runCatching { Json.parseToJsonElement(m.value) as? JsonObject }.getOrNull()
}

/** The line under the button: what it keeps (bold on screen), then the tip and where it came from. */
fun suggestionText(s: BudgetSuggestion, f: BudgetFacts, fromAi: Boolean, saved: Boolean = false, money: (Double) -> String): Pair<String, String> {
    val pct = if (f.income > 0) floor(s.saves / f.income * 100 + 0.5).toInt() else 0
    val keeps = if (s.saves > 0) "Keeps ${money(s.saves)} a month ($pct%) of your ${money(f.income)} average income."
    else if (f.fixed.values.sum() >= f.income) "Your bills take all of your ${money(f.income)} average income, so this only covers them."
    else "This uses all of your ${money(f.income)} average income."
    val tip = if (fromAi && s.tip.isNotEmpty()) " ${s.tip}" else ""
    val how = if (saved) " Your suggestion from earlier this month." else if (fromAi) "" else " Worked out on your device."
    return keeps to "$tip$how Change anything, then save.".trim()
}

/** One AI budget per account per month (Android only): [lastMonth] is the "YYYY-MM" it was last made, or null. */
fun aiBudgetUsed(lastMonth: String?, today: String): Boolean = lastMonth == today.take(7)

const val SUGGEST_NO_RECORDS = "Couldn't read your entries. Check your connection and try again."
const val SUGGEST_NO_INCOME = "Add your income first: the suggestion is based on the last 3 months of it."
const val SUGGEST_NOTHING = "Not enough spending yet to suggest limits. Try again after a month of entries."
