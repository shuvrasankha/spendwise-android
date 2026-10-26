package com.spendwise.app.domain

// Read models for the other collections (spec §3.2–3.5). Their own slices add the write paths.

/** An `income` doc (spec §3.2). */
data class Income(
    val id: String,
    val amount: Double,
    val date: String,
    val source: String,
    val paymentType: String,
    val bank: String,
    val notes: String,
)

/**
 * A debt, always from the viewer's side (spec §3.3): [type] "i-owe" means the viewer owes [person].
 * A [shared] debt is one someone else recorded against the viewer's email, flipped (helpers.js › sharedDebtView).
 */
data class Debt(
    val id: String,
    val amount: Double,
    val type: String,
    val person: String,
    val date: String,
    val notes: String,
    val settled: Boolean,
    val settledDate: String,
    val paid: List<Payment>,
    val claimDate: String = "",
    val claimAmount: Double? = null,
    /** The other person's email: withEmail on your own debts, fromEmail on shared ones. */
    val email: String = "",
    val shared: Boolean = false,
)

const val I_OWE = "i-owe"
const val THEY_OWE = "they-owe"

/** `settings.budget` (spec §3.5), normalized like budget.js › normalize: no total means no budget. */
data class Budget(val total: Double? = null, val cats: Map<String, Double> = emptyMap())

/** A `holdings` doc (spec §3.4). [value] is null unless it's a number ≥ 0 with a valueDate (invest-data.js). */
data class Holding(
    val id: String,
    val name: String,
    val kind: String,
    val value: Double?,
    val valueDate: String?,
    val goalId: String?,
)

/** What the dashboard reads from `settings/{uid}`. */
data class UserSettings(val budget: Budget = Budget(), val goals: List<Goal> = emptyList())
