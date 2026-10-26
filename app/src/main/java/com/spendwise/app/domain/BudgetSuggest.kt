package com.spendwise.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.math.ceil
import kotlin.math.floor

// Ported from budget-data.js (spec §6.8): "Suggest from my income". The last three months are added up on
// the device; the AI gets only those numbers, never an entry, description or name. Its answer is checked
// against the same numbers, and suggestFromFacts gives a plan worked out on the device when it can't help.

private const val LOOKBACK = 3
private const val SAVE_TARGET = 0.2
/** What a budget limits: every category but savings. */
val BUDGET_CATEGORIES = EXPENSE_CATEGORIES - SAVINGS_CATEGORY
const val BUDGET_AI_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"

data class BudgetFacts(
    val months: Int, val income: Double, val spent: Double, val invested: Double,
    val cats: Map<String, Double>, val fixed: Map<String, Double>, val goalsMonthly: Double,
)

data class BudgetSuggestion(val total: Double, val cats: Map<String, Double>, val tip: String, val saves: Double)

/** Math.round: halves go up. */
private fun jsRound(n: Double, step: Double = 100.0) = floor(n / step + 0.5) * step
private fun roundUp(n: Double, step: Double = 100.0) = ceil(n / step) * step

/** Monthly averages over the last three whole months with any entries; null when none of them has income. */
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
    spending.forEach { e ->
        val c = normalizeCategory(e.category)
        cats[c] = (cats[c] ?: 0.0) + e.amount / n
        if (e.repeat == "monthly" || (e.emiTotal ?: 0) > 0) fixed[c] = (fixed[c] ?: 0.0) + e.amount / n
    }
    val goalsMonthly = goals.sumOf { g ->
        if (!(g.target > 0) || !Regex("""^\d{4}-\d{2}$""").matches(g.by)) 0.0
        else goalPace(g, today).let { if (it.level == GoalLevel.ON) it.monthly else 0.0 }
    }
    return BudgetFacts(
        months = n,
        income = income.filter { it.date.take(7) in active }.sumOf { it.amount } / n,
        spent = spending.sumOf { it.amount } / n,
        invested = recent.filter { !isSpending(it.category) }.sumOf { it.amount } / n,
        cats = cats, fixed = fixed, goalsMonthly = goalsMonthly,
    )
}

/** The numbers the AI sees, as plain lines (categories are the app's own fixed names). */
fun factsLines(f: BudgetFacts, symbol: String): String {
    fun m(v: Double) = "$symbol${floor(v + 0.5).toLong()}"
    val byCat = BUDGET_CATEGORIES.filter { (f.cats[it] ?: 0.0) > 0 }.map { c ->
        "- $c: ${m(f.cats.getValue(c))}" + (f.fixed[c]?.takeIf { it != 0.0 }?.let { " (of which ${m(it)} repeating bills/EMIs)" } ?: "")
    }
    return (listOf(
        "Monthly averages over the last ${f.months} ${if (f.months == 1) "month" else "months"}:",
        "Income: ${m(f.income)}",
        "Spending: ${m(f.spent)}",
        "Invested: ${m(f.invested)}",
        if (f.goalsMonthly != 0.0) "Savings goals need: ${m(f.goalsMonthly)} a month" else "Savings goals: none",
        "Spending by category:",
    ) + byCat.ifEmpty { listOf("- none") }).joinToString("\n")
}

/** budgetPrompt: (system, user). */
fun budgetPrompt(f: BudgetFacts, currency: Currency): Pair<String, String> = """You set a monthly spending budget for a SpendWise user from their own numbers. Amounts are in ${currency.code} (${currency.symbol}).
Rules:
- The total must leave money over from income: aim to keep at least 20% of income, more if savings goals need it, but never cut a category below its repeating bills/EMIs.
- Cut flexible categories (food, shopping, entertainment, travel) before essentials (groceries, bills, rent, health, education).
- Only use categories from this list: ${BUDGET_CATEGORIES.joinToString(", ")}. Leave out categories with no spending.
- Whole numbers only. Use only the numbers given; never invent income or spending.
Reply with ONLY one JSON object, no other text:
{"total":<number>,"cats":{"<category>":<number>},"tip":"<one sentence, under 25 words, on where the saving comes from>"}""" to factsLines(f, currency.symbol)

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
fun checkSuggestion(rawTotal: Double, rawCats: List<Pair<String, Double>>, rawTip: String?, f: BudgetFacts): BudgetSuggestion? {
    val cats = LinkedHashMap<String, Double>()
    rawCats.forEach { (name, n) ->
        val c = normalizeCategory(name)
        if (c !in BUDGET_CATEGORIES || !(n > 0) || !((f.cats[c] ?: 0.0) > 0)) return@forEach
        cats[c] = maxOf(roundUp(n), roundUp(f.fixed[c] ?: 0.0))
    }
    val catSum = cats.values.sum()
    var total = if (rawTotal > 0) rawTotal else catSum
    total = maxOf(jsRound(total), catSum)
    if (!(total > 0)) return null
    if (total > f.income) total = maxOf(floor(f.income / 100) * 100, catSum) // over income only when the bills are
    val tip = rawTip?.replace(Regex("\\s+"), " ")?.trim()?.take(160).orEmpty()
    return BudgetSuggestion(total, cats, tip, f.income - total)
}

/** The AI's answer, checked; Number() rules for its values. */
fun checkReply(raw: JsonObject?, f: BudgetFacts): BudgetSuggestion? {
    if (raw == null) return null
    val cats = (raw["cats"] as? JsonObject).orEmpty().map { (k, v) -> k to jsNumberOf(v) }
    return checkSuggestion(jsNumberOf(raw["total"]), cats, (raw["tip"] as? JsonPrimitive)?.takeIf { it.isString }?.content, f)
}

/** JS Number(v) for a JSON value: "8020" is 8020, true is 1, null is 0; missing or not a number is NaN. */
private fun jsNumberOf(v: JsonElement?): Double = when (v) {
    null -> Double.NaN
    JsonNull -> 0.0
    is JsonPrimitive -> when {
        v.isString -> v.content.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }
        v.booleanOrNull != null -> if (v.booleanOrNull == true) 1.0 else 0.0
        else -> v.content.toDoubleOrNull() ?: Double.NaN
    }
    is JsonArray, is JsonObject -> Double.NaN // shortcut: JS reads [] as 0 and [5] as 5; both fail the > 0 checks anyway
}

/** readReply: the JSON object in a reply that may wrap it in a code block or add words around it. */
fun readReply(text: String?): JsonObject? {
    val m = Regex("""\{[\s\S]*\}""").find(text.orEmpty()) ?: return null
    return runCatching { Json.parseToJsonElement(m.value) as? JsonObject }.getOrNull()
}

/** The line under the button: what it keeps (bold on screen), then the tip and where it came from. */
fun suggestionText(s: BudgetSuggestion, f: BudgetFacts, fromAi: Boolean, money: (Double) -> String): Pair<String, String> {
    val pct = if (f.income > 0) floor(s.saves / f.income * 100 + 0.5).toInt() else 0
    val keeps = if (s.saves > 0) "Keeps ${money(s.saves)} a month ($pct%) of your ${money(f.income)} average income."
    else "Your bills take all of your ${money(f.income)} average income, so this only covers them."
    val tip = if (fromAi && s.tip.isNotEmpty()) " ${s.tip}" else ""
    val how = if (fromAi) "" else " Worked out on your device."
    return keeps to "$tip$how Change anything, then save.".trim()
}

const val SUGGEST_NO_RECORDS = "Couldn't read your entries. Check your connection and try again."
const val SUGGEST_NO_INCOME = "Add your income first: the suggestion is based on the last 3 months of it."
const val SUGGEST_NOTHING = "Not enough spending yet to suggest limits. Try again after a month of entries."
