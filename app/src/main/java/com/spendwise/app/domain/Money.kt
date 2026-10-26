package com.spendwise.app.domain

// Ported from helpers.js › budgetState, goalPace, splitAmount (spec §6.1)

/** JS `Math.round(x * 100) / 100`. Java's Math.round rounds halves up, like JS (kotlin.math.round would round to even). */
fun round2(x: Double): Double = Math.round(x * 100) / 100.0

enum class BudgetLevel { OK, NEAR, OVER }

data class BudgetState(val ratio: Double, val left: Double, val level: BudgetLevel)

/** A limit against what's been spent; 'near' from 80%, 'over' past 100%. null when there's no limit. */
fun budgetState(spent: Double, limit: Double?): BudgetState? {
    if (limit == null || !(limit > 0)) return null
    val ratio = spent / limit
    val level = when {
        ratio > 1 -> BudgetLevel.OVER
        ratio >= 0.8 -> BudgetLevel.NEAR
        else -> BudgetLevel.OK
    }
    return BudgetState(ratio, limit - spent, level)
}

/** A savings goal, as stored in `settings.goals[]` (spec §3.5). `by` is "YYYY-MM". */
data class Goal(val id: String, val name: String, val target: Double, val by: String, val saved: Double)

enum class GoalLevel { DONE, ON, LATE }

data class GoalPace(val ratio: Double, val left: Double, val months: Int, val monthly: Double, val level: GoalLevel)

/** [today] is "YYYY-MM-DD"; months left counts the current month. */
fun goalPace(goal: Goal, today: String): GoalPace {
    val left = maxOf(goal.target - goal.saved, 0.0)
    val (y, m) = today.split("-").map(String::toInt)
    val (gy, gm) = goal.by.split("-").map(String::toInt)
    val months = (gy - y) * 12 + (gm - m) + 1
    return GoalPace(
        ratio = if (goal.target > 0) goal.saved / goal.target else 0.0,
        left = left,
        months = months,
        monthly = if (left != 0.0 && months > 0) left / months else 0.0,
        level = when {
            left == 0.0 -> GoalLevel.DONE
            months > 0 -> GoalLevel.ON
            else -> GoalLevel.LATE
        },
    )
}

data class Split(val each: Double, val mine: Double)

/** An equal split between you and [others] people; your share absorbs the rounding. */
fun splitAmount(total: Double, others: Int): Split {
    val each = round2(total / (others + 1))
    return Split(each, round2(total - each * others))
}
