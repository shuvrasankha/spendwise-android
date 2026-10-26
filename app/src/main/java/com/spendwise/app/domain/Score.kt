package com.spendwise.app.domain

// Ported from helpers.js › financeScore (spec §6.3)

data class ScorePart(val name: String, val value: Double)

data class MoneyScore(val score: Int, val label: String, val tip: String, val parts: List<ScorePart>)

private class Part(val name: String, val weight: Int, val value: Double, val tip: String)

private fun clamp(v: Double) = minOf(maxOf(v, 0.0), 1.0)

/**
 * A 0–100 score for this month. Factors without data are left out and the rest scaled up;
 * null until there is income. [monthShare] is day / days in the month; [owe] is what the viewer owes.
 */
fun financeScore(
    income: Double,
    spent: Double,
    invested: Double,
    budget: Double?,
    monthShare: Double,
    owe: Double,
    goals: List<GoalLevel>,
): MoneyScore? {
    if (!(income > 0)) return null
    val parts = mutableListOf(
        Part("Saving", 40, clamp((income - spent) / income / 0.2), "Try to keep 20% of your income unspent"),
        Part("Investing", 15, clamp(invested / income / 0.1), "Putting 10% of your income into investments will lift your score"),
        Part("Debt", 20, clamp(1 - owe / income), "Paying down what you owe will lift your score"),
    )
    // On pace (projected spend within budget) scores full; projected 50% over budget scores zero
    if (budget != null && budget > 0) {
        val projected = spent / monthShare / budget
        parts += Part("Budget pace", 30, clamp((1.5 - projected) / 0.5), "Spending is running ahead of your budget")
    }
    if (goals.isNotEmpty()) {
        parts += Part("Goals", 10, goals.count { it != GoalLevel.LATE }.toDouble() / goals.size, "A savings goal is behind its date")
    }
    val total = parts.sumOf { it.weight }
    val score = Math.round(parts.sumOf { it.weight * it.value } / total * 100).toInt()
    val weakest = parts.reduce { a, b -> if (b.value < a.value) b else a }
    return MoneyScore(
        score = score,
        label = when {
            score >= 80 -> "Great"
            score >= 60 -> "Good"
            score >= 40 -> "Fair"
            else -> "Needs work"
        },
        tip = if (weakest.value < 1) weakest.tip else "Everything is on track this month",
        parts = parts.map { ScorePart(it.name, it.value) },
    )
}
