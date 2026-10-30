package com.spendwise.app.ui.income

import com.spendwise.app.domain.BANK_MAX
import com.spendwise.app.domain.INCOME_NOTES_MAX
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.ONLINE
import com.spendwise.app.domain.SOURCE_MAX
import com.spendwise.app.domain.todayStr

/** The income fields as typed. Online by default with today's date, as income.js › resetIncomeForm. */
data class IncomeForm(
    val amount: String = "",
    val date: String = todayStr(),
    val source: String = "",
    val paymentType: String = ONLINE,
    val bank: String = "",
    val notes: String = "",
) {
    /** Digits and one decimal point only, as on the Add expense form. */
    fun withAmount(v: String) = if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1) copy(amount = v) else this
    fun withSource(v: String) = copy(source = v.take(SOURCE_MAX))
    /** Cash clears the bank (setIncomePayment). */
    fun withPaymentType(v: String) = copy(paymentType = v, bank = if (v == ONLINE) bank else "")
    fun withBank(v: String) = copy(bank = v.take(BANK_MAX))
    fun withNotes(v: String) = copy(notes = v.take(INCOME_NOTES_MAX))

    /** A source chip: the source, plus the payment type and bank last used with it. */
    fun withSourceChip(source: String, last: Income?): IncomeForm {
        val f = copy(source = source)
        if (last == null) return f
        val type = if (last.paymentType == "Cash") "Cash" else ONLINE
        return f.withPaymentType(type).let { if (last.bank.isNotEmpty()) it.copy(bank = last.bank) else it }
    }

    companion object {
        /** An entry's values in the edit fields: 250.0 shows as "250", 182.5 as "182.5". */
        fun of(i: Income) = IncomeForm(
            amount = if (i.amount == Math.floor(i.amount) && !i.amount.isInfinite()) i.amount.toLong().toString()
            else i.amount.toBigDecimal().stripTrailingZeros().toPlainString(),
            date = i.date,
            source = i.source,
            paymentType = i.paymentType.ifEmpty { ONLINE },
            bank = i.bank,
            notes = i.notes,
        )
    }
}

/** Callbacks for the income fields; the New entry sheet and the edit screen both use them. */
interface IncomeFormActions {
    fun setAmount(v: String)
    fun setDate(v: String)
    fun setSource(v: String)
    fun setPaymentType(v: String)
    fun setBank(v: String)
    fun setNotes(v: String)
    fun pickSource(v: String) {}
}
