package com.spendwise.app.domain

// Ported from helpers.js › debtPaid, debtLeft, payDebt, debtClaim (spec §6.1)

/** One part-payment in a debt's `paid` list. [date] is "YYYY-MM-DD". */
data class Payment(val amount: Double, val date: String)

fun debtPaid(paid: List<Payment>): Double = paid.sumOf { it.amount }

/** What's still owed. Every open-debt total uses this, never the original amount. */
fun debtLeft(amount: Double, paid: List<Payment>): Double = maxOf(round2(amount - debtPaid(paid)), 0.0)

sealed interface PayResult {
    /** [paid] is the new list, oldest first. */
    data class Ok(val paid: List<Payment>, val settles: Boolean) : PayResult
    data object NotPositive : PayResult
    data class TooMuch(val left: Double) : PayResult
}

fun payDebt(amount: Double, paid: List<Payment>, payment: Double, date: String): PayResult {
    val left = debtLeft(amount, paid)
    val n = round2(payment)
    if (!(n > 0)) return PayResult.NotPositive
    if (n > left + 0.001) return PayResult.TooMuch(left)
    return PayResult.Ok((paid + Payment(n, date)).sortedBy { it.date }, n >= left - 0.001)
}

data class Claim(val amount: Double, val date: String, val settles: Boolean)

/** What the person a debt is shared with says they paid. A claim without an amount means all that's left. */
fun debtClaim(
    amount: Double,
    paid: List<Payment>,
    settled: Boolean,
    claimDate: String?,
    claimAmount: Double?,
): Claim? {
    if (claimDate.isNullOrEmpty() || settled) return null
    val left = debtLeft(amount, paid)
    val claimed = if (claimAmount != null && claimAmount > 0) minOf(round2(claimAmount), left) else left
    return Claim(claimed, claimDate, claimed >= left - 0.001)
}
