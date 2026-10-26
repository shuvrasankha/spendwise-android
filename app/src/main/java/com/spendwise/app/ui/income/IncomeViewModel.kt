package com.spendwise.app.ui.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.IncomeCheck
import com.spendwise.app.domain.IncomeSummary
import com.spendwise.app.domain.RECENT_COUNT
import com.spendwise.app.domain.checkIncome
import com.spendwise.app.domain.countBy
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.incomeSummary
import com.spendwise.app.domain.lastFromSource
import com.spendwise.app.ui.expenses.FormMessage
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
import java.time.LocalDate
import javax.inject.Inject

sealed interface IncomePage {
    data object Loading : IncomePage
    data class Failed(val message: String) : IncomePage
    data class Ready(
        val all: List<Income>,
        val summary: IncomeSummary,
        val recent: List<Income>,
        /** The 6 most-used sources, as chips under From. */
        val sources: List<String>,
        /** Past banks, most used first, as suggestions. */
        val banks: List<String>,
    ) : IncomePage
}

/** The Income page (spec §1.4): this month so far, the add form, and recent entries. income.js. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomeViewModel @Inject constructor(
    private val repo: IncomeRepository,
    prefs: DevicePrefs,
) : ViewModel(), IncomeFormActions {

    private val reload = MutableStateFlow(0)

    val page: StateFlow<IncomePage> = reload.flatMapLatest {
        repo.income()
            .map<List<Income>, IncomePage> { all ->
                IncomePage.Ready(
                    all = all,
                    summary = incomeSummary(all, LocalDate.now()),
                    recent = all.take(RECENT_COUNT),
                    sources = countBy(all.map { it.source }).take(6),
                    banks = countBy(all.map { it.bank }),
                )
            }
            .onStart { emit(IncomePage.Loading) }
            .catch { emit(IncomePage.Failed("Error loading income data.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomePage.Loading)

    val currency: StateFlow<Currency> = prefs.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    private val _form = MutableStateFlow(IncomeForm())
    val form: StateFlow<IncomeForm> = _form.asStateFlow()

    private val _message = MutableStateFlow<FormMessage?>(null)
    val message: StateFlow<FormMessage?> = _message.asStateFlow()

    fun retry() = reload.update { it + 1 }

    private fun updateForm(change: (IncomeForm) -> IncomeForm) {
        _form.update(change)
        _message.value = null
    }

    override fun setAmount(v: String) = updateForm { it.withAmount(v) }
    override fun setDate(v: String) = updateForm { it.copy(date = v) }
    override fun setSource(v: String) = updateForm { it.withSource(v) }
    override fun setPaymentType(v: String) = updateForm { it.withPaymentType(v) }
    override fun setBank(v: String) = updateForm { it.withBank(v) }
    override fun setNotes(v: String) = updateForm { it.withNotes(v) }
    override fun pickSource(v: String) {
        val all = (page.value as? IncomePage.Ready)?.all.orEmpty()
        updateForm { it.withSourceChip(v, lastFromSource(all, v)) }
    }

    /** resetIncomeForm: empty fields, Online, today. */
    fun clear() = updateForm { IncomeForm() }

    fun add() {
        val f = _form.value
        when (val check = checkIncome(f.amount, f.date, f.source, f.paymentType, f.bank, f.notes, adding = true)) {
            is IncomeCheck.Invalid -> _message.value = FormMessage(check.message)
            is IncomeCheck.Ok -> {
                // Lands in the local cache at once, offline too; only a later refusal from the server is reported
                val write = repo.add(check.input)
                _form.value = IncomeForm()
                _message.value = FormMessage("Income added successfully!", success = true)
                viewModelScope.launch {
                    try {
                        write.confirmed()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _message.value = FormMessage("Failed to save. Try again.")
                    }
                }
            }
        }
    }
}
