package com.spendwise.app.data.expenses

import com.spendwise.app.data.readAmount
import com.spendwise.app.data.toFirestoreNumber
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.ExpenseInput
import com.spendwise.app.domain.normalizeCategory

// The `expenses` doc shape (spec §3.1, §3.7, §3.8, §3.9). Pure, so both directions are unit-tested.

/** Tolerant read, like dashboard.js › loadExpenses: any field but uid may be missing on old docs. */
fun expenseFrom(id: String, data: Map<String, Any?>, pending: Boolean = false) = Expense(
    id = id,
    amount = readAmount(data["amount"]),
    category = normalizeCategory(data["category"]?.toString()),
    date = data["date"] as? String ?: "",
    payment = data["payment"] as? String ?: "",
    cardName = data["cardName"] as? String ?: "",
    description = data["description"] as? String ?: "",
    notes = data["notes"] as? String ?: "",
    tags = (data["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
    repeat = data["repeat"] as? String,
    emiTotal = (data["emiTotal"] as? Number)?.let { Math.floor(it.toDouble()).toInt() },
    emiPaidBefore = (data["emiPaidBefore"] as? Number)?.let { Math.floor(it.toDouble()).toInt() },
    holdingId = data["holdingId"] as? String,
    pending = pending,
)

/** addExpense's document. [createdAt] is FieldValue.serverTimestamp() outside tests. */
fun newExpenseFields(uid: String, input: ExpenseInput, createdAt: Any): Map<String, Any> =
    mapOf("uid" to uid) + editedExpenseFields(input) + ("createdAt" to createdAt) + listOfNotNull(
        input.emiTotal?.let { "emiTotal" to it },
        input.emiPaidBefore?.let { "emiPaidBefore" to it },
    )

/** dashboard.js › addDue: this month's entry of a repeating series, copied from its newest entry. */
fun dueExpenseFields(uid: String, t: Expense, due: String, createdAt: Any): Map<String, Any> = linkedMapOf<String, Any>(
    "uid" to uid,
    "amount" to toFirestoreNumber(t.amount),
    "category" to t.category,
    "date" to due,
    "payment" to t.payment.ifEmpty { "UPI" },
    "cardName" to t.cardName,
    "description" to t.description.ifEmpty { "-" },
    "notes" to "",
    "tags" to t.tags,
    "repeat" to "monthly",
    "encoding" to "plain",
).apply {
    t.holdingId?.takeIf { it.isNotEmpty() }?.let { put("holdingId", it) } // a SIP stays in its holding
    t.emiTotal?.takeIf { it > 0 }?.let { total ->
        put("emiTotal", total)
        t.emiPaidBefore?.takeIf { it > 0 }?.let { put("emiPaidBefore", it) }
    }
    put("createdAt", createdAt)
}

/**
 * saveEditExpense's update, with repeat only when it's set (editedRepeat). Only these fields change, so emiTotal, emiPaidBefore and holdingId
 * stay as they were (spec §2.3, §9.5). Editing also rewrites an old category name as the current one.
 */
fun editedExpenseFields(input: ExpenseInput): Map<String, Any> = mapOf(
    "amount" to toFirestoreNumber(input.amount),
    "category" to input.category,
    "date" to input.date,
    "payment" to input.payment,
    "cardName" to input.cardName,
    "description" to input.description,
    "notes" to input.notes,
    "tags" to input.tags,
    "encoding" to "plain",
) + listOfNotNull(input.repeat?.let { "repeat" to it })
