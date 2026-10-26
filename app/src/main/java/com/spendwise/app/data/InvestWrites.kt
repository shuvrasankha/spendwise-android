package com.spendwise.app.data

import com.spendwise.app.domain.EntryEdit
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.HoldingFields
import com.spendwise.app.domain.InvestEntryInput
import com.spendwise.app.domain.SAVINGS_CATEGORY

// What each investments write sends (spec §2.9, §3.4), matching invest.js. Pure, so it's unit-tested:
// [createdAt] is the server-timestamp sentinel and [delete] the delete-field sentinel.
// holdings take only the keys firestore.rules › validHolding allows: uid, name, kind, value, valueDate, goalId, createdAt.

/** saveHolding's fields, in the web's order: value and valueDate together, goalId only when set. */
private fun holdingFields(f: HoldingFields): Map<String, Any> = buildMap {
    put("name", f.name)
    put("kind", f.kind)
    if (f.value != null) {
        put("value", toFirestoreNumber(f.value))
        f.valueDate?.let { put("valueDate", it) }
    }
    f.goalId?.let { put("goalId", it) }
}

fun newHoldingFields(uid: String, f: HoldingFields, createdAt: Any): Map<String, Any> =
    linkedMapOf<String, Any>("uid" to uid) + holdingFields(f) + ("createdAt" to createdAt)

/** An edit: the fields, plus clearing a value or goal that was set before. */
fun editedHoldingFields(f: HoldingFields, was: Holding, delete: Any): Map<String, Any> = buildMap {
    putAll(holdingFields(f))
    if (f.value == null && was.value != null) {
        put("value", delete)
        put("valueDate", delete)
    }
    if (f.goalId == null && was.goalId != null) put("goalId", delete)
}

/** addEntry's expense, keys in the web's order. */
fun newInvestEntryFields(uid: String, input: InvestEntryInput, createdAt: Any): Map<String, Any> = linkedMapOf<String, Any>(
    "uid" to uid,
    "amount" to toFirestoreNumber(input.amount),
    "category" to SAVINGS_CATEGORY,
    "date" to input.date,
    "payment" to input.payment,
    "cardName" to "",
    "description" to input.holding.name,
    "notes" to input.notes,
    "tags" to emptyList<String>(),
    "holdingId" to input.holding.id,
    "encoding" to "plain",
).apply {
    input.repeat?.let { put("repeat", it) }
    put("createdAt", createdAt)
}

/** saveEntry: moved into a holding, it takes the holding's name; moved out, it loses holdingId. */
fun editedEntryFields(edit: EntryEdit, hadHoldingId: Boolean, delete: Any): Map<String, Any> = buildMap {
    put("amount", toFirestoreNumber(edit.amount))
    put("date", edit.date)
    if (edit.holding != null) {
        put("holdingId", edit.holding.id)
        put("description", edit.holding.name)
    } else if (hadHoldingId) {
        put("holdingId", delete)
    }
}
