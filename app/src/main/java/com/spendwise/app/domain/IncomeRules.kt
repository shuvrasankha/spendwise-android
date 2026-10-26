package com.spendwise.app.domain

// History's income edit dialog (dashboard.js › saveIncEdit, spec §2.5).

const val ONLINE = "Online"
const val CASH = "Cash"
val INCOME_PAYMENT_TYPES = listOf(ONLINE, CASH)

/** Field limits the rules enforce (spec §2.5); the form stops typing past them. */
const val SOURCE_MAX = 100
const val BANK_MAX = 100
const val INCOME_NOTES_MAX = 500

/** The fields an income edit writes, checked and cleaned. [bank] is "" for Cash. */
data class IncomeInput(
    val amount: Double,
    val date: String,
    val source: String,
    val paymentType: String,
    val bank: String,
    val notes: String,
)

sealed interface IncomeCheck {
    data class Ok(val input: IncomeInput) : IncomeCheck
    data class Invalid(val message: String) : IncomeCheck
}

/** saveIncEdit's checks and messages, in its order. */
fun checkIncomeEdit(amount: String, date: String, source: String, paymentType: String, bank: String, notes: String): IncomeCheck {
    val value = jsParseFloat(amount)
    val src = source.trim()
    val bankName = if (paymentType == ONLINE) bank.trim() else ""
    return when {
        !(value > 0) -> IncomeCheck.Invalid("Enter a valid amount.")
        date.isEmpty() -> IncomeCheck.Invalid("Select a date.")
        src.isEmpty() -> IncomeCheck.Invalid("Enter income source.")
        paymentType == ONLINE && bankName.isEmpty() -> IncomeCheck.Invalid("Enter bank/wallet name.")
        else -> IncomeCheck.Ok(IncomeInput(value, date, src, paymentType, bankName, notes.trim()))
    }
}
