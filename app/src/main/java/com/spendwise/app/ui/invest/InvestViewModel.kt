package com.spendwise.app.ui.invest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.spendwise.app.data.DebtRepository
import com.spendwise.app.data.HoldingRepository
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Checked
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.Goal
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.INVEST_PAYMENTS
import com.spendwise.app.domain.Portfolio
import com.spendwise.app.domain.SAVINGS_CATEGORY
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.checkEntryEdit
import com.spendwise.app.domain.checkHolding
import com.spendwise.app.domain.checkInvestEntry
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.holdingOf
import com.spendwise.app.domain.kindOf
import com.spendwise.app.domain.portfolio
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.expenses.FormMessage
import com.spendwise.app.ui.format.formatWhole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.spendwise.app.ui.components.Toast
import javax.inject.Inject

sealed interface InvestPage {
    data object Loading : InvestPage
    data class Failed(val message: String) : InvestPage
    data class Ready(
        val holdings: List<Holding>,
        /** Only investment entries, newest first. */
        val entries: List<Expense>,
        val portfolio: Portfolio,
        val goals: List<Goal>,
    ) : InvestPage
}

/** Put money in, as typed. [repeat] null follows the chosen holding: on when it already has a SIP. */
data class InvestForm(
    val amount: String = "",
    val date: String = todayStr(),
    val payment: String = INVEST_PAYMENTS.first().first,
    val notes: String = "",
    val repeat: Boolean? = null,
)

/** The holding dialog; [id] null for a new one. [confirmDelete] asks before deleting. */
data class HoldingDialog(
    val id: String?, val name: String, val kind: String, val value: String, val goalId: String,
    val error: String? = null, val confirmDelete: Boolean = false,
)

/** The entry dialog; [holdingId] "" is Not in a holding. */
data class EntryDialog(
    val id: String, val holdingId: String, val amount: String, val date: String,
    val error: String? = null, val confirmDelete: Boolean = false,
)

/** The Investments page (spec §1.8, §2.9): invest.js. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InvestViewModel @Inject constructor(
    private val holdingRepo: HoldingRepository,
    private val expenseRepo: ExpenseRepository,
    settingsRepo: SettingsRepository,
    debtRepo: DebtRepository,
    prefs: DevicePrefs,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    val page: StateFlow<InvestPage> = reload.flatMapLatest {
        combine(
            holdingRepo.holdings(),
            expenseRepo.expenses().map { all -> all.filter { it.category == SAVINGS_CATEGORY } },
            settingsRepo.settings().catch { emit(UserSettings()) },
        ) { holdings, entries, settings ->
            InvestPage.Ready(holdings, entries, portfolio(holdings, entries, todayStr()), settings.goals) as InvestPage
        }
            .onStart { emit(InvestPage.Loading) }
            .catch { e ->
                // Before the updated rules are published, holdings can't be read: say so plainly
                emit(
                    InvestPage.Failed(
                        if (e.denied()) "Investments aren't switched on for this account yet. The app's security rules need an update."
                        else "Couldn't load your investments. Check your connection and reload.",
                    ),
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvestPage.Loading)

    /** Debts only feed "After debts", so they never hold up the page. */
    val debts: StateFlow<List<Debt>> = debtRepo.debts().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currency: StateFlow<Currency> = prefs.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    private fun whole(n: Double) = formatWhole(n, currency.value)

    private val _chosen = MutableStateFlow<String?>(null)
    val chosenId: StateFlow<String?> = _chosen.asStateFlow()

    /** The holding the form puts money into: the one picked, else the first. */
    fun chosen(p: InvestPage.Ready): Holding? = p.holdings.firstOrNull { it.id == _chosen.value } ?: p.holdings.firstOrNull()

    private val _form = MutableStateFlow(InvestForm())
    val form: StateFlow<InvestForm> = _form.asStateFlow()
    private val _formMessage = MutableStateFlow<FormMessage?>(null)
    val formMessage: StateFlow<FormMessage?> = _formMessage.asStateFlow()

    private val _holding = MutableStateFlow<HoldingDialog?>(null)
    val holdingDialog: StateFlow<HoldingDialog?> = _holding.asStateFlow()
    private val _entry = MutableStateFlow<EntryDialog?>(null)
    val entryDialog: StateFlow<EntryDialog?> = _entry.asStateFlow()
    /** An entry waiting for the holding being made from its "Into" list; it reopens with that holding picked. */
    private var entryAwaiting: EntryDialog? = null

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()
    fun toastShown() { _toast.value = null }

    fun retry() = reload.update { it + 1 }

    private fun ready() = page.value as? InvestPage.Ready

    // ── Put money in ──

    fun choose(id: String) {
        _chosen.value = id
        updateForm { it.copy(repeat = null) }
    }

    private fun updateForm(change: (InvestForm) -> InvestForm) {
        _form.update(change)
        _formMessage.value = null
    }

    fun setAmount(v: String) = updateForm { if (v.all { c -> c.isDigit() || c == '.' } && v.count { c -> c == '.' } <= 1) it.copy(amount = v) else it }
    fun setDate(v: String) = updateForm { it.copy(date = v) }
    fun setPayment(v: String) = updateForm { it.copy(payment = v) }
    fun setNotes(v: String) = updateForm { it.copy(notes = v.take(500)) }
    fun setRepeat(v: Boolean) = updateForm { it.copy(repeat = v) }

    fun add() {
        val p = ready() ?: return
        val f = _form.value
        val h = chosen(p)
        val sip = p.portfolio.rows.firstOrNull { it.holding.id == h?.id }?.sip
        val placeholder = sip?.let { plain(it.amount) } ?: ""
        when (val c = checkInvestEntry(h, f.amount, placeholder, f.date, f.payment, f.notes, f.repeat ?: (sip != null), wasSip = sip != null)) {
            is Checked.Invalid -> _formMessage.value = FormMessage(c.message)
            is Checked.Ok -> {
                watch(expenseRepo.addInvestment(c.value)) { _formMessage.value = FormMessage("Couldn't save it. Check your connection and try again.") }
                _toast.value = Toast("${whole(c.value.amount)} put into ${c.value.holding.name}.")
                _form.update { it.copy(amount = "", notes = "", repeat = null) }
            }
        }
    }

    // ── Holding dialog ──

    fun newHolding(name: String = "") { _holding.value = HoldingDialog(null, name, "mf", "", "") }

    fun editHolding(id: String) {
        val p = ready() ?: return
        val h = p.holdings.firstOrNull { it.id == id } ?: return
        _holding.value = HoldingDialog(
            id = h.id, name = h.name,
            kind = kindOf(h.kind).id,
            value = h.value?.let(::plain) ?: "",
            goalId = h.goalId?.takeIf { g -> p.goals.any { it.id == g } } ?: "",
        )
    }

    fun setHolding(change: (HoldingDialog) -> HoldingDialog) = _holding.update { it?.let(change)?.copy(error = null) }

    /** Closed without making one: back to the waiting entry as it was. */
    fun closeHolding() {
        _holding.value = null
        entryAwaiting?.let { _entry.value = it }
        entryAwaiting = null
    }

    fun saveHolding() {
        val p = ready() ?: return
        val d = _holding.value ?: return
        val was = p.holdings.firstOrNull { it.id == d.id }
        when (val c = checkHolding(d.name, d.kind, d.value, d.goalId, was, p.holdings, todayStr())) {
            is Checked.Invalid -> _holding.update { it?.copy(error = c.message) }
            is Checked.Ok -> {
                val failed = { e: Exception ->
                    _toast.value = Toast(if (e.denied()) "This account can't save holdings yet. The app's security rules need an update."
                    else "Couldn't save it. Check your connection and try again.", error = true)
                }
                _holding.value = null
                if (was != null) {
                    watch(holdingRepo.edit(was, c.value), failed)
                    _toast.value = Toast("Holding saved.")
                    return
                }
                val write = holdingRepo.add(c.value)
                watch(write, failed)
                _chosen.value = write.id
                updateForm { it.copy(repeat = null) }
                val awaiting = entryAwaiting
                entryAwaiting = null
                if (awaiting != null) {
                    _entry.value = awaiting.copy(holdingId = write.id)
                    _toast.value = Toast("${c.value.name} added. Save to move the money into it.")
                } else {
                    _toast.value = Toast("${c.value.name} added. Now put money in.")
                }
            }
        }
    }

    fun askDeleteHolding() = _holding.update { it?.copy(confirmDelete = true) }
    fun cancelDeleteHolding() = _holding.update { it?.copy(confirmDelete = false) }

    /** Its SIP stops too, so the Dashboard doesn't keep offering it; its entries keep their name. */
    fun deleteHolding() {
        val p = ready() ?: return
        val h = p.holdings.firstOrNull { it.id == _holding.value?.id } ?: return
        val failed = { _: Exception -> _toast.value = Toast("Couldn't delete it. Check your connection and try again.", error = true) }
        val sip = p.portfolio.rows.firstOrNull { it.holding.id == h.id }?.sip
        if (sip != null && !sip.template.pending) watch(expenseRepo.stopRepeat(sip.template.id), failed)
        watch(holdingRepo.delete(h.id), failed)
        if (_chosen.value == h.id) _chosen.value = null
        _holding.value = null
        _toast.value = Toast("Holding deleted.")
    }

    // ── Entry dialog ──

    fun openEntry(id: String) {
        val p = ready() ?: return
        val e = p.entries.firstOrNull { it.id == id }?.takeIf { !it.pending } ?: return
        _entry.value = EntryDialog(e.id, holdingOf(e, p.holdings)?.id ?: "", plain(e.amount), e.date)
    }

    fun setEntry(change: (EntryDialog) -> EntryDialog) = _entry.update { it?.let(change)?.copy(error = null) }
    fun closeEntry() { _entry.value = null }

    /** "New holding…": make one named after the entry, when it has a name, then come back. */
    fun newHoldingForEntry() {
        val d = _entry.value ?: return
        val e = ready()?.entries?.firstOrNull { it.id == d.id } ?: return
        entryAwaiting = d
        _entry.value = null
        newHolding(if (e.description.isNotEmpty() && e.description != "-") e.description else "")
    }

    fun saveEntry() {
        val p = ready() ?: return
        val d = _entry.value ?: return
        val e = p.entries.firstOrNull { it.id == d.id } ?: return closeEntry()
        when (val c = checkEntryEdit(d.amount, d.date, p.holdings.firstOrNull { it.id == d.holdingId })) {
            is Checked.Invalid -> _entry.update { it?.copy(error = c.message) }
            is Checked.Ok -> {
                watch(expenseRepo.editInvestment(e.id, c.value, hadHoldingId = e.holdingId != null)) {
                    _toast.value = Toast("Couldn't save it. Check your connection and try again.", error = true)
                }
                _entry.value = null
                _toast.value = Toast("Saved.")
            }
        }
    }

    fun askDeleteEntry() = _entry.update { it?.copy(confirmDelete = true) }
    fun cancelDeleteEntry() = _entry.update { it?.copy(confirmDelete = false) }
    fun deleteEntry() {
        val d = _entry.value ?: return
        watch(expenseRepo.delete(d.id)) { _toast.value = Toast("Couldn't delete it. Check your connection and try again.", error = true) }
        _entry.value = null
        _toast.value = Toast("Deleted.")
    }

    /** Writes land in the local cache at once, offline too; only a later refusal from the server is reported. */
    private fun watch(write: PendingWrite, failed: (Exception) -> Unit) = viewModelScope.launch {
        try {
            write.confirmed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(e)
        }
    }

    private fun Throwable.denied() = (this as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
}

/** As JS String(n): 5000.0 shows as "5000", 2500.5 as "2500.5". */
internal fun plain(n: Double): String = n.toBigDecimal().stripTrailingZeros().toPlainString()
