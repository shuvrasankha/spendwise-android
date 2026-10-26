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
    /** "monthly", "stopped", or null to leave it off. */
    val repeat: String? = null,
    /** Only with repeat, on Add; the edit never changes them (spec §9.5). */
    val emiTotal: Int? = null,
    val emiPaidBefore: Int? = null,
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

/** JS Number(text) as a whole number, or null when it isn't one. */
private fun wholeNumber(text: String): Int? = text.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 && Math.abs(it) < Int.MAX_VALUE }?.toInt()

/**
 * addExpense's checks in order (the split's come after, in split.js), then its cleaning. [repeat] is the
 * "Repeats every month" switch; the EMI fields only count while it's on. The edit passes neither EMI field.
 */
fun checkExpense(
    amountText: String,
    category: String,
    date: String,
    payment: String,
    cardName: String,
    description: String,
    notes: String,
    tags: List<String>,
    repeat: Boolean = false,
    emiTotalText: String = "",
    emiBeforeText: String = "",
): ExpenseCheck {
    val amount = jsParseFloat(amountText)
    if (!(amount > 0)) return ExpenseCheck.Invalid("Enter a valid amount.")
    val totalRaw = if (repeat) emiTotalText.trim() else ""
    val beforeRaw = if (repeat) emiBeforeText.trim() else ""
    val emiTotal = if (totalRaw.isNotEmpty()) wholeNumber(totalRaw) else 0
    val emiBefore = if (beforeRaw.isNotEmpty()) wholeNumber(beforeRaw) else 0
    if (totalRaw.isNotEmpty() && (emiTotal == null || emiTotal !in 2..600)) {
        return ExpenseCheck.Invalid("Total payments should be a whole number from 2 to 600.")
    }
    val total = emiTotal ?: 0
    if (beforeRaw.isNotEmpty() && (emiBefore == null || emiBefore < 0 || (total != 0 && emiBefore >= total))) {
        return ExpenseCheck.Invalid(
            if (total != 0) "Already paid should be a whole number below $total, counting this payment out." else "Already paid needs Total payments too.",
        )
    }
    val before = emiBefore ?: 0
    if (beforeRaw.isNotEmpty() && before > 0 && total == 0) return ExpenseCheck.Invalid("Add Total payments for this EMI.")
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
            repeat = if (repeat) "monthly" else null,
            emiTotal = total.takeIf { it > 0 },
            emiPaidBefore = if (total > 0 && before > 0) before else null,
        ),
    )
}

/** saveEditExpense: ticking starts a series; unticking a repeating entry stops it; otherwise it stays as it was. */
fun editedRepeat(on: Boolean, was: String?): String? = if (on) "monthly" else if (was == "monthly") "stopped" else was

/** dashboard.js › updateExpenseCards (spec §6.4). */
data class ExpenseFigures(
    val month: Double, val monthCount: Int,
    val year: Double, val yearCount: Int,
    val all: Double, val allCount: Int,
    val perDay: Double,
)

fun expenseFigures(expenses: List<Expense>, today: java.time.LocalDate): ExpenseFigures {
    val todayS = today.toString()
    val ms = today.withDayOfMonth(1).toString()
    val spending = expenses.filter { isSpending(it.category) }
    val month = spending.filter { it.date >= ms && it.date <= todayS }
    // web quirk: §9.3 — the year counts future-dated entries too
    val year = spending.filter { it.date.startsWith(today.year.toString()) }
    val monthTotal = month.sumOf { it.amount }
    return ExpenseFigures(
        monthTotal, month.size, year.sumOf { it.amount }, year.size, spending.sumOf { it.amount }, spending.size,
        monthTotal / today.dayOfMonth,
    )
}

/** The same amount, category and date again within 2 minutes is usually a double tap: the web asks once. */
const val DUPLICATE_WINDOW_MS = 120_000L

fun duplicateKey(input: ExpenseInput) = "${input.amount}|${input.category}|${input.date}"
