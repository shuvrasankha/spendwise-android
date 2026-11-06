package com.spendwise.app.ui.expenses

import com.spendwise.app.domain.SMS_READ
import com.spendwise.app.domain.SMS_UNREAD
import com.spendwise.app.domain.SmsEntry
import com.spendwise.app.ui.navigation.SharedText
import com.spendwise.app.ui.navigation.ShareInbox
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.DUPLICATE_WINDOW_MS
import com.spendwise.app.domain.ExpenseCheck
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.domain.Checked
import com.spendwise.app.domain.tagSuggestions
import com.spendwise.app.domain.usedTags
import com.spendwise.app.domain.DebtInput
import com.spendwise.app.domain.MAX_SPLIT_PEOPLE
import com.spendwise.app.domain.SplitRow
import com.spendwise.app.domain.addTags
import com.spendwise.app.domain.checkSplit
import com.spendwise.app.domain.editedRepeat
import com.spendwise.app.domain.sanitize
import com.spendwise.app.domain.splitAmount
import com.spendwise.app.domain.splitDebts
import com.spendwise.app.domain.splitSaved
import com.spendwise.app.domain.checkExpense
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.duplicateKey
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.format.formatMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.spendwise.app.data.DebtRepository
import com.spendwise.app.domain.CATEGORY_TILE_LABELS
import com.spendwise.app.domain.KnownPerson
import com.spendwise.app.domain.orderedTiles
import com.spendwise.app.domain.peopleFrom
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import javax.inject.Inject
import com.spendwise.app.data.ai.AiCall
import com.spendwise.app.data.ai.AiGateway
import com.spendwise.app.data.ai.AiResult
import com.spendwise.app.data.ai.AiTask
import com.spendwise.app.data.ai.textMessages
import com.spendwise.app.domain.categoryFromText
import com.spendwise.app.domain.categoryPrompt
import com.spendwise.app.domain.readCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class ExpenseForm(
    val amount: String = "",
    val date: String = todayStr(),
    val category: String = "",
    val payment: String = "",
    val cardName: String = "",
    val description: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    /** Text typed in the tag field, not yet turned into tags. */
    val tagDraft: String = "",
    /** "Repeats every month"; the EMI fields count only while it's on, and only on Add. */
    val repeat: Boolean = false,
    val emiTotal: String = "",
    val emiBefore: String = "",
    /** Add only: the people to split with. */
    val split: List<SplitRow> = emptyList(),
)

/** The snackbar after an Add: "Expense added." or "Split saved.", whose Undo deletes all of it. */
data class AddedUndo(val id: String, val debtIds: List<String>, val label: String)

data class FormMessage(val text: String, val success: Boolean = false)

data class EditorState(
    /** null for Add; the doc id when editing. */
    val editingId: String? = null,
    /** Editing: the doc is still loading, or [missing] if it couldn't be found. */
    val loading: Boolean = false,
    val missing: Boolean = false,
    val form: ExpenseForm = ExpenseForm(),
    val message: FormMessage? = null,
    val confirmDelete: Boolean = false,
    /** Add: what was just saved, for the Undo snackbar. */
    val undo: AddedUndo? = null,
    /** Split rows can take an email only on a verified account (firestore.rules › validShare). */
    val canShare: Boolean = false,
    /** Edit: saved or deleted, so the screen closes. */
    val done: Boolean = false,
    /** The category tiles as (category, label); on Add, ordered by recent use once per visit. */
    val tiles: List<Pair<String, String>> = CATEGORY_TILE_LABELS.toList(),
    /** Add: people from past debts, suggested on the split rows (people.js). */
    val known: List<KnownPerson> = emptyList(),
    /** Tags from past expenses, most used first, offered under the tag field. */
    val usedTags: List<String> = emptyList(),
    /** A category the description points to, offered under it; [hintByAi] when the AI read it. */
    val categoryHint: String? = null,
    val hintByAi: Boolean = false,
) {
    val suggestedTags: List<String> get() = tagSuggestions(usedTags, form.tags, form.tagDraft)
}

/** The Add form, and the edit screen for one expense (route arg "id"). Spec §1.3, §2.3. */
@HiltViewModel
class ExpenseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ExpenseRepository,
    debtRepo: DebtRepository,
    private val prefs: DevicePrefs,
    private val auth: AuthRepository,
    inbox: ShareInbox,
    private val ai: AiGateway,
) : ViewModel() {

    private val editingId: String? = savedStateHandle["id"]

    private val _state = MutableStateFlow(EditorState(editingId = editingId, loading = editingId != null, canShare = auth.currentUser?.emailVerified == true))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val currency: StateFlow<Currency> = prefs.currency
        .stateIn(viewModelScope, SharingStarted.Eagerly, currencyOf(null))

    /** Editing: the doc's repeat as loaded, so unticking stops a series (editedRepeat). */
    private var wasRepeat: String? = null
    private var lastAdded: Pair<String, Long>? = null
    private var confirmedDuplicate: String? = null

    init {
        viewModelScope.launch {
            if (editingId == null) {
                val payment = prefs.defaultPayment.first()
                updateForm { it.copy(payment = payment) }
                // Once, from the first non-empty load: the web orders tiles once per visit, after expenses arrive
                val expenses = repo.expenses().catch { }.firstOrNull { it.isNotEmpty() }
                if (expenses != null) _state.update { it.copy(tiles = orderedTiles(expenses, LocalDate.now())) }
            } else {
                loadForEdit(editingId)
            }
        }
    }

    init {
        // Suggestions are a nicety: a debts load error just leaves them empty, as the web's people.js does
        if (editingId == null) {
            debtRepo.debts().map { peopleFrom(it) }.catch { emit(emptyList()) }
                .onEach { known -> _state.update { it.copy(known = known) } }
                .launchIn(viewModelScope)
        }
    }

    init {
        // A load error just leaves the suggestions empty
        repo.expenses().map { usedTags(it) }.catch { emit(emptyList()) }
            .onEach { used -> _state.update { it.copy(usedTags = used) } }
            .launchIn(viewModelScope)
    }

    init {
        // Add only: a shared SMS that isn't money in, or any other shared text
        if (editingId == null) {
            inbox.shared.onEach { inbox.take { it.entry !is SmsEntry.Earned }?.let(::prefill) }.launchIn(viewModelScope)
        }
    }

    private fun prefill(s: SharedText) {
        val e = s.entry as? SmsEntry.Spent
        _state.update {
            it.copy(
                form = if (e != null) {
                    ExpenseForm(amount = e.amount.toPlainString(), date = e.date, category = e.category, payment = e.payment, cardName = e.cardName, description = e.description)
                } else {
                    ExpenseForm(payment = it.form.payment, description = sanitize(s.text.replace(Regex("\\s+"), " "), 300))
                },
                message = FormMessage(if (e != null) SMS_READ else SMS_UNREAD, success = e != null),
            )
        }
    }

    private suspend fun loadForEdit(id: String) {
        val expense = try {
            repo.expense(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _state.update {
            if (expense == null) {
                it.copy(loading = false, missing = true)
            } else {
                wasRepeat = expense.repeat
                it.copy(
                    loading = false,
                    form = ExpenseForm(
                        amount = expense.amount.toPlainString(),
                        date = expense.date,
                        category = expense.category,
                        payment = expense.payment,
                        cardName = expense.cardName,
                        description = expense.description,
                        notes = expense.notes,
                        tags = expense.tags,
                        repeat = expense.repeat == "monthly",
                    ),
                )
            }
        }
    }

    private fun updateForm(change: (ExpenseForm) -> ExpenseForm) = _state.update { it.copy(form = change(it.form), message = null) }

    /** Digits and one decimal point only, so "12,5" can't be read as 12 by parseFloat. */
    fun setAmount(v: String) {
        if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1) updateForm { it.copy(amount = v) }
    }
    fun setDate(v: String) = updateForm { it.copy(date = v) }
    fun setCategory(v: String) = updateForm { it.copy(category = v) }
    fun setPayment(v: String) = updateForm { it.copy(payment = v) }
    fun setCardName(v: String) = updateForm { it.copy(cardName = v) }
    fun setDescription(v: String) {
        updateForm { it.copy(description = v) }
        suggestCategory(v)
    }

    private var hintJob: Job? = null

    /**
     * The brand or kind in the description first, on the device. Only with the user's own key does a description
     * the device can't place go to the AI: typing never spends the free daily allowance or opens a consent dialog.
     */
    private fun suggestCategory(description: String) {
        hintJob?.cancel()
        val text = description.trim()
        categoryFromText(text)?.let { c -> return _state.update { it.copy(categoryHint = c, hintByAi = false) } }
        _state.update { it.copy(categoryHint = null) }
        if (text.length < 3) return
        hintJob = viewModelScope.launch {
            delay(900) // after typing pauses
            // Own key only: it was agreed to when it was turned on, so no other consent is needed
            if (!ai.byok()) return@launch
            val res = try {
                ai.complete(AiCall(AiTask.VOICE, textMessages(listOf("system" to categoryPrompt(), "user" to text.take(120))), maxTokens = 20, temperature = 0.0))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch // a hint is optional: no error for it
            }
            val c = (res as? AiResult.Ok)?.content?.let(::readCategory) ?: return@launch
            _state.update { if (it.form.description.trim() == text) it.copy(categoryHint = c, hintByAi = true) else it }
        }
    }
    fun setNotes(v: String) = updateForm { it.copy(notes = v) }

    /** Like the web's tag field, a comma or space commits what's been typed. */
    fun setTagDraft(v: String) = updateForm {
        if (v.endsWith(',') || v.endsWith(' ')) it.copy(tags = addTags(it.tags, v), tagDraft = "") else it.copy(tagDraft = v)
    }
    fun commitTagDraft() = updateForm { it.copy(tags = addTags(it.tags, it.tagDraft), tagDraft = "") }
    /** A suggested tag tapped: added, and whatever was half-typed toward it cleared. */
    fun addSuggestedTag(tag: String) = updateForm { it.copy(tags = addTags(it.tags, tag), tagDraft = "") }
    fun removeTag(tag: String) = updateForm { it.copy(tags = it.tags - tag) }
    fun clearTags() = updateForm { it.copy(tags = emptyList()) }

    fun setRepeat(v: Boolean) = updateForm { it.copy(repeat = v) }
    fun setEmiTotal(v: String) = updateForm { it.copy(emiTotal = v) }
    fun setEmiBefore(v: String) = updateForm { it.copy(emiBefore = v) }

    fun addSplitPerson() = updateForm { if (it.split.size < MAX_SPLIT_PEOPLE) it.copy(split = it.split + SplitRow()) else it }
    fun setSplitPerson(i: Int, row: SplitRow) = updateForm { f ->
        // people.js: an email you've used before fills in their name, if none is typed
        val old = f.split[i]
        val known = if (row.email != old.email && row.name.isBlank()) {
            _state.value.known.firstOrNull { it.email.isNotEmpty() && it.email == row.email.trim().lowercase() }
        } else null
        f.copy(split = f.split.toMutableList().also { l -> l[i] = if (known != null) row.copy(name = known.name) else row })
    }

    /** A suggestion: their name, and their email when known and this account can share (people.js). */
    fun pickSplitPerson(i: Int, p: KnownPerson) = setSplitPerson(
        i, f(i).copy(name = p.name, email = if (p.email.isNotEmpty() && _state.value.canShare) p.email else f(i).email),
    )

    private fun f(i: Int) = _state.value.form.split[i]
    fun removeSplitPerson(i: Int) = updateForm { it.copy(split = it.split.filterIndexed { j, _ -> j != i }) }

    fun clear() = viewModelScope.launch {
        val payment = prefs.defaultPayment.first()
        _state.update { it.copy(form = ExpenseForm(payment = payment), message = null) }
    }

    fun save() {
        val form = _state.value.form
        val tags = addTags(form.tags, form.tagDraft) // the web adds a half-typed tag on blur
        val check = checkExpense(
            form.amount, form.category, form.date, form.payment, form.cardName, form.description, form.notes, tags,
            repeat = form.repeat,
            emiTotalText = if (editingId == null) form.emiTotal else "",
            emiBeforeText = if (editingId == null) form.emiBefore else "",
        )
        if (check is ExpenseCheck.Invalid) return _state.update { it.copy(message = FormMessage(check.message)) }
        var input = (check as ExpenseCheck.Ok).input

        if (editingId != null) {
            watch(repo.edit(editingId, input.copy(repeat = editedRepeat(form.repeat, wasRepeat))), failure = "Couldn't save your changes. Check your connection and try again.")
            _state.update { it.copy(done = true) }
            return
        }

        // Split: your share becomes the expense, and each person owes you theirs
        var debts = emptyList<DebtInput>()
        when (val split = checkSplit(if (_state.value.canShare) form.split else form.split.map { it.copy(email = "") }, auth.currentUser?.email.orEmpty())) {
            null -> Unit
            is Checked.Invalid -> return _state.update { it.copy(message = FormMessage(split.message)) }
            is Checked.Ok -> {
                val (each, mine) = splitAmount(input.amount, split.value.size)
                val label = sanitize(form.description.trim(), 300).ifEmpty { input.category }
                debts = splitDebts(split.value, each, input.date, label)
                input = input.copy(amount = mine)
            }
        }

        val key = duplicateKey(input)
        val now = System.currentTimeMillis()
        val last = lastAdded
        if (last != null && last.first == key && now - last.second < DUPLICATE_WINDOW_MS && confirmedDuplicate != key) {
            confirmedDuplicate = key
            val text = "You added ${formatMoney(input.amount, currency.value)} for ${input.category} a moment ago. Tap Add Expense again to save it twice."
            return _state.update { it.copy(message = FormMessage(text)) }
        }
        val (write, debtIds) = if (debts.isEmpty()) repo.add(input) to emptyList() else repo.addSplit(input, debts)
        watch(write, failure = "Couldn't save the expense. Check your connection and try again.")
        lastAdded = key to now
        val done = if (debts.isEmpty()) "Expense added successfully!" else splitSaved(debts.size, formatMoney(debts[0].amount, currency.value))
        _state.update {
            it.copy(
                form = ExpenseForm(payment = input.payment),
                message = FormMessage(done, success = true),
                undo = AddedUndo(write.id, debtIds, if (debts.isEmpty()) "Expense added." else "Split saved."),
            )
        }
    }

    fun undoShown() = _state.update { it.copy(undo = null) }

    /** Undo: deletes the expense just saved, and a split's debts with it. */
    fun undo(added: AddedUndo) {
        val write = if (added.debtIds.isEmpty()) repo.delete(added.id) else repo.deleteSplit(added.id, added.debtIds)
        watch(write, failure = "Couldn't undo. Delete it from Today's list instead.")
        _state.update { it.copy(message = FormMessage("Expense removed.", success = true)) }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }
    fun confirmDelete() {
        val id = editingId ?: return
        watch(repo.delete(id), failure = "Couldn't delete the expense. Check your connection and try again.")
        _state.update { it.copy(confirmDelete = false, done = true) }
    }

    /** Writes land in the local cache at once; only a refusal from the server is reported, later. */
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

/** 250.0 → "250", 182.5 → "182.5", as the web shows the amount in its edit field. */
private fun Double.toPlainString(): String =
    if (this == Math.floor(this) && !isInfinite()) toLong().toString() else toBigDecimal().stripTrailingZeros().toPlainString()
