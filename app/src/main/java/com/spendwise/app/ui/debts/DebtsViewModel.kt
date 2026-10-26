package com.spendwise.app.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.spendwise.app.data.DebtRepository
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.DebtCheck
import com.spendwise.app.domain.DebtPerson
import com.spendwise.app.domain.DebtSummary
import com.spendwise.app.domain.KnownPerson
import com.spendwise.app.domain.PayResult
import com.spendwise.app.domain.THEY_OWE
import com.spendwise.app.domain.checkDebt
import com.spendwise.app.domain.claim
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.debtLeft
import com.spendwise.app.domain.debtSummary
import com.spendwise.app.domain.debtsByPerson
import com.spendwise.app.domain.isPersons
import com.spendwise.app.domain.left
import com.spendwise.app.domain.parsePayAmount
import com.spendwise.app.domain.payDebt
import com.spendwise.app.domain.payError
import com.spendwise.app.domain.peopleFrom
import com.spendwise.app.domain.reopenPaid
import com.spendwise.app.domain.round2
import com.spendwise.app.domain.settledDebts
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.expenses.FormMessage
import com.spendwise.app.ui.format.formatMoney
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

sealed interface DebtsPage {
    data object Loading : DebtsPage
    data class Failed(val message: String) : DebtsPage
    data class Ready(
        val all: List<Debt>,
        val summary: DebtSummary,
        val people: List<DebtPerson>,
        /** The By person card shows once there's more than one open debt to add up. */
        val showPeople: Boolean,
        val settled: List<Debt>,
        val known: List<KnownPerson>,
    ) : DebtsPage
}

/** The add form as typed; they-owe with today's date, as debt.js › resetDebtForm. */
data class DebtForm(
    val type: String = THEY_OWE,
    val person: String = "",
    val email: String = "",
    val amount: String = "",
    val date: String = todayStr(),
    val notes: String = "",
)

/** The payment dialog for one debt: the amount starts at what's left, the date at today. */
data class PayDialog(val id: String, val amount: String, val date: String = todayStr(), val error: String? = null)

/** The Debts page (spec §1.5, §2.7): debt.js, debt-data.js and split.js. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val repo: DebtRepository,
    private val auth: AuthRepository,
    prefs: DevicePrefs,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    val page: StateFlow<DebtsPage> = reload.flatMapLatest {
        repo.debts()
            .map<List<Debt>, DebtsPage> { all ->
                DebtsPage.Ready(
                    all = all,
                    summary = debtSummary(all),
                    people = debtsByPerson(all),
                    showPeople = all.count { !it.settled } > 1,
                    settled = settledDebts(all),
                    known = peopleFrom(all),
                )
            }
            .onStart { emit(DebtsPage.Loading) }
            .catch { emit(DebtsPage.Failed("Error loading debt data.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsPage.Loading)

    val currency: StateFlow<Currency> = prefs.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    private fun fmt(n: Double) = formatMoney(n, currency.value)

    /** Sharing needs a verified email (firestore.rules › validShare). */
    val canShare: Boolean get() = auth.currentUser?.emailVerified == true

    private val _form = MutableStateFlow(DebtForm())
    val form: StateFlow<DebtForm> = _form.asStateFlow()
    private val _formMessage = MutableStateFlow<FormMessage?>(null)
    val formMessage: StateFlow<FormMessage?> = _formMessage.asStateFlow()

    /** The By person row the Open list is narrowed to, by key. */
    private val _filter = MutableStateFlow<String?>(null)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    private val _pay = MutableStateFlow<PayDialog?>(null)
    val pay: StateFlow<PayDialog?> = _pay.asStateFlow()
    private val _deleting = MutableStateFlow<String?>(null)
    val deleting: StateFlow<String?> = _deleting.asStateFlow()
    private val _reopening = MutableStateFlow<String?>(null)
    val reopening: StateFlow<String?> = _reopening.asStateFlow()

    /** One toast at a time, as the web's showToast. */
    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()
    fun toastShown() { _toast.value = null }

    fun retry() = reload.update { it + 1 }

    private fun debt(id: String?) = (page.value as? DebtsPage.Ready)?.all?.firstOrNull { it.id == id }

    /** Open debts, narrowed to the chosen person. A person no longer in the list drops the filter. */
    fun open(p: DebtsPage.Ready): Pair<DebtPerson?, List<Debt>> {
        val person = p.people.firstOrNull { it.key == _filter.value }?.takeIf { p.showPeople }
        return person to p.all.filter { !it.settled && (person == null || isPersons(it, person)) }
    }

    fun toggleFilter(key: String) = _filter.update { if (it == key) null else key }
    fun clearFilter() { _filter.value = null }

    // ── Add ──

    private fun updateForm(change: (DebtForm) -> DebtForm) {
        _form.update(change)
        _formMessage.value = null
    }

    fun setType(v: String) = updateForm { it.copy(type = v) }
    fun setPerson(v: String) = updateForm { it.copy(person = v.take(100)) }
    fun setEmail(v: String) = updateForm { f ->
        // people.js: an email you've used before fills in their name, if none is typed
        val known = (page.value as? DebtsPage.Ready)?.known.orEmpty().firstOrNull { it.email.isNotEmpty() && it.email == v.trim().lowercase() }
        f.copy(email = v.take(254), person = if (known != null && f.person.isBlank()) known.name else f.person)
    }
    fun setAmount(v: String) = updateForm { if (v.all { c -> c.isDigit() || c == '.' } && v.count { c -> c == '.' } <= 1) it.copy(amount = v) else it }
    fun setDate(v: String) = updateForm { it.copy(date = v) }
    fun setNotes(v: String) = updateForm { it.copy(notes = v.take(500)) }
    /** A suggestion: their name, and their email when known (people.js). */
    fun pickPerson(p: KnownPerson) = updateForm { it.copy(person = p.name, email = if (p.email.isNotEmpty() && canShare) p.email else it.email) }

    fun clear() = updateForm { DebtForm() }

    fun add() {
        val f = _form.value
        val email = if (canShare) f.email else ""
        when (val check = checkDebt(f.amount, f.type, f.person, f.date, f.notes, email, auth.currentUser?.email.orEmpty())) {
            is DebtCheck.Invalid -> _formMessage.value = FormMessage(check.message)
            is DebtCheck.Ok -> {
                val write = repo.add(check.input)
                _form.value = DebtForm()
                val mail = check.input.email
                _formMessage.value = FormMessage(if (mail.isNotEmpty()) "Debt added and shared with $mail." else "Debt added successfully!", success = true)
                watch(write) { _formMessage.value = FormMessage("Failed to save. Try again.") }
            }
        }
    }

    // ── Payments and claims ──

    fun openPay(id: String) {
        val d = debt(id) ?: return
        val left = d.left()
        _pay.value = PayDialog(id, if (left == 0.0) "" else plain(left))
    }
    fun setPayAmount(v: String) = _pay.update { it?.copy(amount = v, error = null) }
    fun setPayDate(v: String) = _pay.update { it?.copy(date = v, error = null) }
    fun closePay() { _pay.value = null }

    /** confirmDebtSettle: the owner records it; on a shared debt it's a claim for them to confirm. */
    fun savePay() {
        val dialog = _pay.value ?: return
        val d = debt(dialog.id) ?: return closePay()
        val n = parsePayAmount(dialog.amount)
        payError(d, n, dialog.date, ::fmt)?.let { e -> _pay.update { it?.copy(error = e) }; return }
        if (d.shared) {
            watch(repo.claim(d.id, round2(n!!), dialog.date)) { e ->
                _toast.value = Toast(if (e.denied()) "SpendWise can't send this yet. Try again later." else "Couldn't send it. Check your connection and try again.", error = true)
            }
            _toast.value = Toast("Sent. ${d.person} will confirm it.")
        } else {
            val r = payDebt(d.amount, d.paid, n!!, dialog.date) as PayResult.Ok
            watch(repo.pay(d.id, r.paid, r.settles, dialog.date)) { _toast.value = Toast("Couldn't save the payment. Check your connection and try again.", error = true) }
            _toast.value = Toast(if (r.settles) "Paid off. Debt settled!" else "Payment saved. ${fmt(debtLeft(d.amount, r.paid))} left.")
        }
        closePay()
    }

    /** A payment entered by mistake (owner only): it comes off, and what's left goes back up. */
    fun removePayment(index: Int) {
        val d = debt(_pay.value?.id) ?: return
        if (d.shared) return
        watch(repo.removePayment(d.id, d.paid.filterIndexed { i, _ -> i != index })) {
            _toast.value = Toast("Couldn't remove it. Check your connection and try again.", error = true)
        }
        _toast.value = Toast("Payment removed.")
    }

    /** Undo (theirs) and Not yet (the owner's) clear the claim; Confirm counts it as a payment. */
    fun undoClaim(id: String) = clearClaim(id, "Taken back.")
    fun rejectClaim(id: String) = clearClaim(id, "Not counted. They can say so again once it’s paid.")

    private fun clearClaim(id: String, done: String) {
        debt(id) ?: return
        watch(repo.clearClaim(id), ::claimFailed)
        _toast.value = Toast(done)
    }

    fun confirmClaim(id: String) {
        val d = debt(id)?.takeIf { !it.shared } ?: return
        val c = d.claim() ?: return
        when (val r = payDebt(d.amount, d.paid, c.amount, c.date)) {
            is PayResult.Ok -> {
                watch(repo.confirmClaim(d.id, r.paid, r.settles, c.date), ::claimFailed)
                _toast.value = Toast(if (r.settles) "Paid off. Debt settled!" else "Payment confirmed.")
            }
            else -> claimFailed(null)
        }
    }

    // permission-denied: the published rules don't allow it (firestore.rules › debts), not the network
    private fun claimFailed(e: Exception?) {
        _toast.value = Toast(if (e?.denied() == true) "SpendWise can't update this debt yet. Try again later."
        else "Couldn't update the debt. Check your connection and try again.", error = true)
    }

    // ── Delete and Reopen (owner only) ──

    fun askDelete(id: String) { _deleting.value = id }
    fun cancelDelete() { _deleting.value = null }
    fun confirmDelete() {
        val d = debt(_deleting.value)?.takeIf { !it.shared }
        _deleting.value = null
        if (d == null) return
        watch(repo.delete(d.id)) { _toast.value = Toast("Delete failed.", error = true) }
        _toast.value = Toast("Debt deleted.")
    }

    fun askReopen(id: String) { _reopening.value = id }
    fun cancelReopen() { _reopening.value = null }
    fun confirmReopen() {
        val d = debt(_reopening.value)?.takeIf { !it.shared }
        _reopening.value = null
        if (d == null) return
        watch(repo.reopen(d.id, reopenPaid(d))) { _toast.value = Toast("Reactivation failed.", error = true) }
        _toast.value = Toast("Debt reactivated!")
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

    private fun Exception.denied() = (this as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    /** What's left in the amount box: 1268.0 shows as "1268", 182.5 as "182.5". */
    private fun plain(n: Double) = n.toBigDecimal().stripTrailingZeros().toPlainString()
}
