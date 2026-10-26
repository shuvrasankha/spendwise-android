package com.spendwise.app.domain

// Expenses (spec §1.3, §2.3, §3.1). Ported from dashboard.js › addExpense, saveEditExpense, addTag, toggleCardName
// and helpers.js › sanitize.

/** An `expenses` doc as read. [category] is already normalized, like the web's loadExpenses. */
data class Expense(
    val id: String,
    val amount: Double,
    val category: String,
    val date: String,
    val payment: String,
    val cardName: String,
    val description: String,
    val notes: String,
    val tags: List<String>,
    val repeat: String? = null,
    val emiTotal: Int? = null,
    val emiPaidBefore: Int? = null,
    val holdingId: String? = null,
    /** Written on this device and not yet synced: it can't be edited or deleted until it is (spec §2.3). */
    val pending: Boolean = false,
)

/** The fields the Add form and the edit dialog write, already checked and cleaned. */
data class ExpenseInput(
    val amount: Double,
    val category: String,
    val date: String,
    val payment: String,
    val cardName: String,
    val description: String,
    val notes: String,
    val tags: List<String>,
)

/** In the order the web's select lists them. The default is the last one used, else UPI. */
val PAYMENT_METHODS = listOf("UPI", "Cash", "Credit Card", "Debit Card", "Net Banking", "Other")
const val DEFAULT_PAYMENT = "UPI"

fun isCard(payment: String) = payment == "Credit Card" || payment == "Debit Card"

/** The short labels on the web's category tiles, in tile order. */
val CATEGORY_TILE_LABELS = mapOf(
    "Food & Dining" to "Food", "Groceries" to "Groceries", "Transport" to "Transport", "Travel" to "Travel",
    "Shopping" to "Shopping", "Bills & Utilities" to "Bills", "Home & Rent" to "Home", "Health" to "Health",
    "Entertainment" to "Fun", "Education" to "Education", "Savings & Investments" to "Savings", "Other" to "Other",
)

private val controlCharacters = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]")

/** helpers.js › sanitize: strip control characters (newlines and tabs stay), cap the length, trim. */
fun sanitize(text: String, maxLength: Int = 500): String =
    controlCharacters.replace(text, "").take(maxLength).trim()

/** addTag: commas and spaces separate tags, a leading # is dropped, and duplicates are skipped. */
fun addTags(existing: List<String>, typed: String): List<String> {
    val new = typed.replace(',', ' ').trim().split(Regex("\\s+"))
        .map { it.removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
    return new.fold(existing) { tags, tag -> if (tag in tags) tags else tags + tag }
}

sealed interface ExpenseCheck {
    data class Ok(val input: ExpenseInput) : ExpenseCheck
    data class Invalid(val message: String) : ExpenseCheck
}

/** addExpense's checks in order (without the EMI and split ones, which come later), then its cleaning. */
fun checkExpense(
    amountText: String,
    category: String,
    date: String,
    payment: String,
    cardName: String,
    description: String,
    notes: String,
    tags: List<String>,
): ExpenseCheck {
    val amount = jsParseFloat(amountText)
    if (!(amount > 0)) return ExpenseCheck.Invalid("Enter a valid amount.")
    if (category.isEmpty()) return ExpenseCheck.Invalid("Select a category.")
    if (date.isEmpty()) return ExpenseCheck.Invalid("Select a date.")
    return ExpenseCheck.Ok(
        ExpenseInput(
            amount = amount,
            category = category,
            date = date,
            payment = payment,
            // toggleCardName clears the field when the method isn't a card
            cardName = if (isCard(payment)) sanitize(cardName.trim(), 100) else "",
            description = sanitize(description.trim(), 300).ifEmpty { "-" },
            notes = sanitize(notes.trim(), 500),
            tags = tags,
        ),
    )
}

/** The same amount, category and date again within 2 minutes is usually a double tap: the web asks once. */
const val DUPLICATE_WINDOW_MS = 120_000L

fun duplicateKey(input: ExpenseInput) = "${input.amount}|${input.category}|${input.date}"
