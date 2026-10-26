package com.spendwise.app.ui.income

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.domain.BANK_MAX
import com.spendwise.app.domain.INCOME_NOTES_MAX
import com.spendwise.app.domain.IncomeCheck
import com.spendwise.app.domain.ONLINE
import com.spendwise.app.domain.SOURCE_MAX
import com.spendwise.app.domain.checkIncomeEdit
import com.spendwise.app.ui.expenses.FormMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncomeForm(
    val amount: String = "",
    val date: String = "",
    val source: String = "",
    val paymentType: String = ONLINE,
    val bank: String = "",
    val notes: String = "",
)

data class IncomeEditorState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val form: IncomeForm = IncomeForm(),
    val message: FormMessage? = null,
    val confirmDelete: Boolean = false,
    /** Saved or deleted, so the screen closes. */
    val done: Boolean = false,
)

/** History's income edit (dashboard.js › openIncEdit, saveIncEdit, confirmIncDelete; spec §2.5). Route arg "id". */
@HiltViewModel
class IncomeEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: IncomeRepository,
) : ViewModel() {

    private val id: String = checkNotNull(savedStateHandle["id"])

    private val _state = MutableStateFlow(IncomeEditorState())
    val state: StateFlow<IncomeEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val income = try {
                repo.income(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update {
                if (income == null || income.pending) {
                    it.copy(loading = false, missing = true)
                } else {
                    it.copy(
                        loading = false,
                        form = IncomeForm(
                            amount = income.amount.toPlainString(),
                            date = income.date,
                            source = income.source,
                            paymentType = income.paymentType.ifEmpty { ONLINE },
                            bank = income.bank,
                            notes = income.notes,
                        ),
                    )
                }
            }
        }
    }

    private fun updateForm(change: (IncomeForm) -> IncomeForm) = _state.update { it.copy(form = change(it.form), message = null) }

    /** Digits and one decimal point only, as on the Add expense form. */
    fun setAmount(v: String) {
        if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1) updateForm { it.copy(amount = v) }
    }
    fun setDate(v: String) = updateForm { it.copy(date = v) }
    fun setSource(v: String) = updateForm { it.copy(source = v.take(SOURCE_MAX)) }
    /** Cash clears the bank, as toggleIncEditBank does. */
    fun setPaymentType(v: String) = updateForm { it.copy(paymentType = v, bank = if (v == ONLINE) it.bank else "") }
    fun setBank(v: String) = updateForm { it.copy(bank = v.take(BANK_MAX)) }
    fun setNotes(v: String) = updateForm { it.copy(notes = v.take(INCOME_NOTES_MAX)) }

    fun save() {
        val f = _state.value.form
        when (val check = checkIncomeEdit(f.amount, f.date, f.source, f.paymentType, f.bank, f.notes)) {
            is IncomeCheck.Invalid -> _state.update { it.copy(message = FormMessage(check.message)) }
            is IncomeCheck.Ok -> {
                watch(repo.edit(id, check.input), "Failed to update. Try again.")
                _state.update { it.copy(done = true) }
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }
    fun confirmDelete() {
        watch(repo.delete(id), "Delete failed.")
        _state.update { it.copy(confirmDelete = false, done = true) }
    }

    /** Writes land in the local cache at once; only a later refusal from the server is reported. */
    private fun watch(write: PendingWrite, failure: String) = viewModelScope.launch {
        try {
            write.confirmed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(message = FormMessage(failure)) }
        }
    }
}

/** 250.0 → "250", 182.5 → "182.5", as the web fills its edit field. */
private fun Double.toPlainString(): String =
    if (this == Math.floor(this) && !isInfinite()) toLong().toString() else toBigDecimal().stripTrailingZeros().toPlainString()
