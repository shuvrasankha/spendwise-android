package com.spendwise.app.domain

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Budgets, savings goals and splitting an expense (spec §2.3, §2.8, §6.1). Ported from budget.js, goals.js
// and split.js. Wording that carries an amount takes [whole], the page's whole-number money formatter.

/** budget.js / goals.js › num: digits and dots only, then parseFloat; a positive amount rounded to cents, else null. */
fun moneyNum(text: String): Double? {
    val n = jsParseFloat(text.replace(Regex("[^\\d.]"), ""))
    return if (n > 0) round2(n) else null
}

// ── Budget (budget.js › save) ────────────────────────────────────────────────

/** [cats] maps each category to the text typed for it; blank or unreadable limits are left out. */
fun checkBudget(totalText: String, cats: Map<String, String>): Checked<Budget> {
    val total = moneyNum(totalText)
    if (totalText.isNotBlank() && total == null) return Checked.Invalid("Enter the monthly limit as a number, or leave it empty.")
    val limits = LinkedHashMap<String, Double>()
    cats.forEach { (c, text) -> moneyNum(text)?.let { limits[c] = it } }
    return Checked.Ok(Budget(total, limits))
}

// ── Goals (goals.js) ─────────────────────────────────────────────────────────

const val MAX_GOALS = 20

/** The goals list after a change, and the goal it just took past its target (Home celebrates it). */
data class GoalsChange(val goals: List<Goal>, val completed: String?)

/** "Add money": [goal] is as saved in settings, without what's invested towards it. */
fun addToGoal(goals: List<Goal>, goal: Goal, amountText: String): Checked<GoalsChange> {
    val amount = moneyNum(amountText) ?: return Checked.Invalid("Enter how much you put aside.")
    val saved = round2(goal.saved + amount)
    val wasDone = goal.saved >= goal.target
    return Checked.Ok(
        GoalsChange(goals.map { if (it.id == goal.id) it.copy(saved = saved) else it }, goal.id.takeIf { !wasDone && saved >= goal.target }),
    )
}

/** A new goal ([goal] null, appended with [newId]) or an edit. [by] is "YYYY-MM". */
fun saveGoal(goals: List<Goal>, goal: Goal?, name: String, targetText: String, by: String, savedText: String, newId: String): Checked<GoalsChange> {
    val trimmed = name.trim()
    val target = moneyNum(targetText)
    val saved = moneyNum(savedText) ?: 0.0
    if (trimmed.isEmpty()) return Checked.Invalid("Give the goal a name.")
    if (target == null) return Checked.Invalid("Enter the amount as a number.")
    if (!Regex("^\\d{4}-\\d{2}$").matches(by)) return Checked.Invalid("Pick the month you want to reach it by.")
    if (savedText.isNotBlank() && saved == 0.0) return Checked.Invalid("Enter what you have saved as a number, or leave it empty.")
    val wasDone = goal != null && goal.saved >= goal.target
    val next = Goal(goal?.id ?: newId, trimmed, target, by, saved)
    val list = if (goal != null) goals.map { if (it.id == goal.id) next else it } else goals + next
    return Checked.Ok(GoalsChange(list, next.id.takeIf { !wasDone && saved >= target }))
}

/** Date.now().toString(36), the web's goal id. */
fun newGoalId(nowMs: Long = System.currentTimeMillis()): String = nowMs.toString(36)

/** A goal on Home, its saved amount including what's invested towards it (dashboard.js › renderGoals). */
data class GoalRow(val goal: Goal, val linked: Double, val pace: GoalPace) {
    val percent get() = Math.round(minOf(pace.ratio, 1.0) * 100).toInt()

    fun amounts(whole: (Double) -> String) = "${whole(goal.saved)} of ${whole(goal.target)}"

    fun meta(whole: (Double) -> String) = when (pace.level) {
        GoalLevel.DONE -> "Reached. Well done!"
        GoalLevel.LATE -> "${whole(pace.left)} to go · ${monthYear(goal.by)} has passed"
        GoalLevel.ON -> "${whole(pace.monthly)} a month · by ${monthYear(goal.by)}"
    }
}

fun goalRows(goals: List<Goal>, linked: Map<String, Double>, today: String): List<GoalRow> = goals.map {
    val g = withLinked(it, linked)
    GoalRow(g, linked[it.id] ?: 0.0, goalPace(g, today))
}

/** The goals header: what they need a month, against this month's income − spent ([saved], null without income). */
fun goalsNeed(rows: List<GoalRow>, saved: Double?, whole: (Double) -> String): String? {
    val need = rows.sumOf { it.pace.monthly }
    if (!(need > 0)) return null
    return "${whole(need)} a month" + when {
        saved == null -> ""
        saved >= need -> " · covered"
        else -> " · ${whole(maxOf(saved, 0.0))} left this month"
    }
}

private val monthYearFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

/** "2027-03" → "Mar 2027" (en-US). */
fun monthYear(month: String): String = runCatching { YearMonth.parse(month).format(monthYearFormat) }.getOrDefault(month)

// ── Split (split.js) ─────────────────────────────────────────────────────────

const val MAX_SPLIT_PEOPLE = 10

/** One row of the split list, as typed. */
data class SplitRow(val name: String = "", val email: String = "")

data class SplitPerson(val name: String, val email: String)

/** getSplit: null when not splitting; rows with neither a name nor an email are ignored. */
fun checkSplit(rows: List<SplitRow>, myEmail: String): Checked<List<SplitPerson>>? {
    val people = rows.map { SplitPerson(it.name.trim(), it.email.trim().lowercase()) }.filter { it.name.isNotEmpty() || it.email.isNotEmpty() }
    if (people.isEmpty()) return null
    for (p in people) {
        if (p.name.isEmpty()) return Checked.Invalid("Enter a name for each person you split with.")
        emailError(p.email, myEmail)?.let { return Checked.Invalid(it) }
    }
    return Checked.Ok(people.map { it.copy(name = sanitize(it.name, 100)) })
}

/** One "they owe me" debt per person, for the share each owes, on the expense's date (splitDebts). */
fun splitDebts(people: List<SplitPerson>, each: Double, date: String, label: String) = people.map {
    DebtInput(each, THEY_OWE, it.name, date, sanitize("Split: $label", 500), it.email)
}

/** The hint ("2 people") and the note under the split list (split.js › updateSummary). */
fun splitSummary(rows: List<SplitRow>, amountText: String, money: (Double) -> String): Pair<String, String> {
    val people = rows.count { it.name.trim().isNotEmpty() }
    if (people == 0) return "" to "Add the people who shared this. It is split equally."
    val hint = "$people ${if (people == 1) "person" else "people"}"
    val amount = jsParseFloat(amountText)
    if (!(amount > 0)) return hint to "Enter the full amount above to see each share."
    val (each, mine) = splitAmount(amount, people)
    return hint to "Your share ${money(mine)} is saved as the expense · each person owes you ${money(each)}"
}

fun splitSaved(people: Int, each: String) =
    "Your share added. ${if (people == 1) "1 person owes" else "$people people owe"} you $each${if (people > 1) " each" else ""}, see Debts."
