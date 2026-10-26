package com.spendwise.app.data

import com.spendwise.app.domain.DebtInput
import com.spendwise.app.domain.Payment
import com.spendwise.app.domain.sanitize

// What each debt write sends (spec §3.3, §4.2), matching debt.js and split.js. Pure, so it's unit-tested:
// [createdAt] is the server-timestamp sentinel and [delete] the delete-field sentinel.
// Every update leaves uid, withEmail, fromEmail and fromName alone, as the rules require of the owner.

/** split.js › shareFields: who it's shared with and who shared it. Only for a verified account (validShare). */
data class Share(val withEmail: String, val fromName: String, val fromEmail: String) {
    companion object {
        fun of(email: String, displayName: String, myEmail: String) =
            Share(email, sanitize(displayName.ifEmpty { myEmail }, 100), myEmail.lowercase())
    }
}

/** debt.js › addDebt: the whole new doc, keys in the web's order. */
fun newDebtFields(uid: String, input: DebtInput, share: Share?, createdAt: Any): Map<String, Any> = linkedMapOf<String, Any>(
    "uid" to uid,
    "amount" to toFirestoreNumber(input.amount),
    "type" to input.type,
    "person" to input.person,
    "date" to input.date,
    "notes" to input.notes,
    "settled" to false,
    "settledDate" to "",
    "encoding" to "plain",
    "createdAt" to createdAt,
).apply {
    if (share != null) {
        put("withEmail", share.withEmail)
        put("fromName", share.fromName)
        put("fromEmail", share.fromEmail)
    }
}

fun paidField(paid: List<Payment>): List<Map<String, Any>> =
    paid.map { linkedMapOf("amount" to toFirestoreNumber(it.amount), "date" to it.date) }

/** The owner records a payment (confirmDebtSettle). Paying it off settles it on that date and clears a claim date. */
fun payFields(paid: List<Payment>, settles: Boolean, date: String, delete: Any): Map<String, Any> =
    if (settles) mapOf("paid" to paidField(paid), "settled" to true, "settledDate" to date, "claimDate" to delete)
    else mapOf("paid" to paidField(paid))

/** The person it's shared with says what was paid (split.js › claimSettled). Only these two fields, as the rules allow. */
fun claimFields(amount: Double, date: String): Map<String, Any> = mapOf("claimDate" to date, "claimAmount" to toFirestoreNumber(amount))

/** Taking a claim back (theirs) or not counting it (the owner's): split.js › withdrawClaim / rejectClaim. */
fun clearClaimFields(delete: Any): Map<String, Any> = mapOf("claimDate" to delete, "claimAmount" to delete)

/** The owner agrees the claim was paid (split.js › confirmClaim). */
fun confirmClaimFields(paid: List<Payment>, settles: Boolean, date: String, delete: Any): Map<String, Any> =
    buildMap {
        put("paid", paidField(paid))
        if (settles) {
            put("settled", true)
            put("settledDate", date)
        }
        put("claimDate", delete)
        put("claimAmount", delete)
    }

/** debt.js › confirmDebtReactivate; [paid] is non-null when the last payment comes off (reopenPaid). */
fun reopenFields(paid: List<Payment>?): Map<String, Any> =
    buildMap {
        put("settled", false)
        put("settledDate", "")
        if (paid != null) put("paid", paidField(paid))
    }
