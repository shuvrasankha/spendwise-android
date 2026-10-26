package com.spendwise.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.todayStr
import com.spendwise.app.domain.emiPlans
import com.spendwise.app.domain.expenseFigures
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.spendwise.app.ui.components.Toast
import javax.inject.Inject

sealed interface ExpenseList {
    data object Loading : ExpenseList
    data class Failed(val message: String) : ExpenseList
    data class Ready(val all: List<Expense>) : ExpenseList {
        /** The Add page's "Today" list (spec §1.3). */
        val today get() = todayStr().let { day -> all.filter { it.date == day } }
        /** The Add page's summary figures and EMIs card (spec §6.4, §6.5). */
        val figures get() = expenseFigures(all, LocalDate.now())
        val emis get() = emiPlans(all, todayStr())
    }
}

/** The expense list (History tab, and Today on the Add tab), with delete. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repo: ExpenseRepository,
    prefs: DevicePrefs,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    val list: StateFlow<ExpenseList> = reload.flatMapLatest {
        repo.expenses()
            .map<List<Expense>, ExpenseList> { ExpenseList.Ready(it) }
            .onStart { emit(ExpenseList.Loading) }
            .catch { emit(ExpenseList.Failed("Couldn't load your expenses. Check your connection and try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseList.Loading)

    val currency: StateFlow<Currency> = prefs.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    /** The expense waiting for "Delete this expense?" to be confirmed. */
    private val _confirmDelete = MutableStateFlow<Expense?>(null)
    val confirmDelete: StateFlow<Expense?> = _confirmDelete.asStateFlow()

    private val _message = MutableStateFlow<Toast?>(null)
    val message: StateFlow<Toast?> = _message.asStateFlow()

    fun retry() = reload.update { it + 1 }
    fun askDelete(expense: Expense) = _confirmDelete.update { expense }
    fun cancelDelete() = _confirmDelete.update { null }
    fun messageShown() = _message.update { null }

    fun confirmDelete() {
        val expense = _confirmDelete.value ?: return
        _confirmDelete.update { null }
        val write = repo.delete(expense.id)
        _message.update { Toast("Expense deleted.") }
        viewModelScope.launch {
            try {
                write.confirmed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.update { Toast("Couldn't delete the expense. Check your connection and try again.", error = true) }
            }
        }
    }
}
