package com.spendwise.app.ui.income

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.app.domain.BANK_MAX
import com.spendwise.app.domain.INCOME_NOTES_MAX
import com.spendwise.app.domain.INCOME_PAYMENT_TYPES
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.ONLINE
import com.spendwise.app.domain.SOURCE_MAX
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.expenses.DateField

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

/** Callbacks for [IncomeFormFields]; both the add form and the edit screen implement them. */
interface IncomeFormActions {
    fun setAmount(v: String)
    fun setDate(v: String)
    fun setSource(v: String)
    fun setPaymentType(v: String)
    fun setBank(v: String)
    fun setNotes(v: String)
    fun pickSource(v: String) {}
}

/**
 * Amount, date, From, Online/Cash, bank, notes. [sources] are chips under From and [banks] suggestions under
 * Bank; the edit dialogs have neither, so they pass none.
 */
@Composable
fun IncomeFormFields(form: IncomeForm, actions: IncomeFormActions, sources: List<String> = emptyList(), banks: List<String> = emptyList()) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            form.amount, actions::setAmount, label = { Text("Amount") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        DateField(form.date, actions::setDate)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(form.source, actions::setSource, label = { Text("From") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (sources.isNotEmpty()) Chips(sources, actions::pickSource)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Received as", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                INCOME_PAYMENT_TYPES.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = form.paymentType == type,
                        onClick = { actions.setPaymentType(type) },
                        shape = SegmentedButtonDefaults.itemShape(i, INCOME_PAYMENT_TYPES.size),
                    ) { Text(type) }
                }
            }
        }
        if (form.paymentType == ONLINE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(form.bank, actions::setBank, label = { Text("Bank or wallet") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // Like the web's datalist: past banks containing what's typed, most used first
                val typed = form.bank.trim().lowercase()
                val matches = banks.filter { it.lowercase().contains(typed) && !it.equals(form.bank.trim(), ignoreCase = true) }.take(6)
                if (matches.isNotEmpty()) Chips(matches, actions::setBank)
            }
        }
        OutlinedTextField(form.notes, actions::setNotes, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Chips(values: List<String>, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v -> SuggestionChip(onClick = { onPick(v) }, label = { Text(v) }) }
    }
}
