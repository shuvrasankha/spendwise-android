package com.spendwise.app.ui.income

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.domain.IncomeCheck
import com.spendwise.app.domain.checkIncome
import com.spendwise.app.ui.expenses.FormMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncomeEditorState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val form: IncomeForm = IncomeForm(),
    val message: FormMessage? = null,
    val confirmDelete: Boolean = false,
    /** Saved or deleted, so the screen closes. */
    val done: Boolean = false,
)

/** Edit one income entry, from History or the Income page (saveIncEdit / saveEditIncome; spec §2.5). Route arg "id". */
@HiltViewModel
class IncomeEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: IncomeRepository,
) : ViewModel(), IncomeFormActions {

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
                        form = IncomeForm.of(income),
                    )
                }
            }
        }
    }

    private fun updateForm(change: (IncomeForm) -> IncomeForm) = _state.update { it.copy(form = change(it.form), message = null) }

    override fun setAmount(v: String) = updateForm { it.withAmount(v) }
    override fun setDate(v: String) = updateForm { it.copy(date = v) }
    override fun setSource(v: String) = updateForm { it.withSource(v) }
    override fun setPaymentType(v: String) = updateForm { it.withPaymentType(v) }
    override fun setBank(v: String) = updateForm { it.withBank(v) }
    override fun setNotes(v: String) = updateForm { it.withNotes(v) }

    fun save() {
        val f = _state.value.form
        when (val check = checkIncome(f.amount, f.date, f.source, f.paymentType, f.bank, f.notes, adding = false)) {
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
