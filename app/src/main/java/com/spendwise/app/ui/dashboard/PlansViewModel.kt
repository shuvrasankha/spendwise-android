package com.spendwise.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.worker.WorkerClient
import com.spendwise.app.data.worker.aiContent
import com.spendwise.app.data.worker.aiRequest
import com.spendwise.app.domain.BUDGET_AI_MODEL
import com.spendwise.app.domain.BudgetFacts
import com.spendwise.app.domain.BudgetSuggestion
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.SUGGEST_NOTHING
import com.spendwise.app.domain.SUGGEST_NO_INCOME
import com.spendwise.app.domain.SUGGEST_NO_RECORDS
import com.spendwise.app.domain.budgetFacts
import com.spendwise.app.domain.budgetPrompt
import com.spendwise.app.domain.checkReply
import com.spendwise.app.domain.readReply
import com.spendwise.app.domain.suggestFromFacts
import com.spendwise.app.domain.suggestionText
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.format.formatWhole
import kotlinx.coroutines.flow.first
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Budget
import com.spendwise.app.domain.Checked
import com.spendwise.app.domain.DueBill
import com.spendwise.app.domain.Goal
import com.spendwise.app.domain.GoalsChange
import com.spendwise.app.domain.MAX_GOALS
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.addToGoal
import com.spendwise.app.domain.billTitle
import com.spendwise.app.domain.checkBudget
import com.spendwise.app.domain.newGoalId
import com.spendwise.app.domain.saveGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A snackbar on Home; [undoId] is an expense its Undo deletes. */
data class Notice(val text: String, val undoId: String? = null, val error: Boolean = false)

/** "Suggest from my income": [fill] goes into the dialog's fields for a look before saving. */
data class SuggestState(val working: Boolean = false, val keeps: String = "", val text: String = "", val error: Boolean = false, val fill: BudgetSuggestion? = null)

/** The goal dialog: "add" money to [goal], or edit it ([goal] null for a new one). */
data class GoalEditing(val adding: Boolean, val goal: Goal?)

/** Home's actions: due bills, the budget dialog and the goal dialog (dashboard.js, budget.js, goals.js). */
@HiltViewModel
class PlansViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    private val settingsRepo: SettingsRepository,
    private val prefs: DevicePrefs,
    private val income: IncomeRepository,
    private val worker: WorkerClient,
) : ViewModel() {

    private val settings: StateFlow<UserSettings> = settingsRepo.settings().catch { emit(UserSettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()
    fun noticeShown() = _notice.update { null }

    // ── Due this month ──

    fun addDue(bill: DueBill) {
        val write = expenses.addDue(bill.template, bill.due)
        watch(write, "Couldn't do that. Check your connection and try again.")
        _notice.update { Notice("${billTitle(bill.template)} added.", undoId = write.id) }
    }

    fun undoDue(id: String) = watch(expenses.delete(id), "Couldn't undo. Delete it from History instead.")

    fun skip(bill: DueBill) = viewModelScope.launch { prefs.skipBill(bill.key, bill.due.take(7)) }

    fun stop(bill: DueBill) {
        if (bill.template.pending) return _notice.update { Notice("This one is still waiting to sync. Try again once you're online.") }
        watch(expenses.stopRepeat(bill.template.id), "Couldn't do that. Check your connection and try again.")
        _notice.update { Notice("It won't be suggested again. Past entries stay.") }
    }

    // ── Budget ──

    private val _budgetOpen = MutableStateFlow(false)
    val budgetOpen: StateFlow<Boolean> = _budgetOpen.asStateFlow()
    private val _budgetError = MutableStateFlow<String?>(null)
    val budgetError: StateFlow<String?> = _budgetError.asStateFlow()

    /** The budget as saved, to fill the dialog. */
    val budget: Budget get() = settings.value.budget

    fun openBudget() { _budgetError.update { null }; _suggest.update { null }; _budgetOpen.update { true } }
    fun closeBudget() = _budgetOpen.update { false }

    fun saveBudget(totalText: String, cats: Map<String, String>) {
        when (val c = checkBudget(totalText, cats)) {
            is Checked.Invalid -> _budgetError.update { c.message }
            is Checked.Ok -> writeBudget(c.value)
        }
    }

    fun removeBudget() = writeBudget(Budget())

    private val _suggest = MutableStateFlow<SuggestState?>(null)
    val suggest: StateFlow<SuggestState?> = _suggest.asStateFlow()

    /** budget.js › suggest: the AI's plan when it can be asked and holds up, else one worked out here. */
    fun suggestBudget() {
        if (_suggest.value?.working == true) return
        _suggest.update { SuggestState(working = true) }
        viewModelScope.launch {
            val facts = try {
                budgetFacts(expenses.expenses().first(), income.income().first(), settings.value.goals, todayStr())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch _suggest.update { SuggestState(text = SUGGEST_NO_RECORDS, error = true) }
            }
            if (facts == null) return@launch _suggest.update { SuggestState(text = SUGGEST_NO_INCOME, error = true) }
            val currency = prefs.currency.first()
            val ai = askAi(facts, currency)
            val s = ai ?: suggestFromFacts(facts) ?: return@launch _suggest.update { SuggestState(text = SUGGEST_NOTHING, error = true) }
            val (keeps, rest) = suggestionText(s, facts, ai != null) { formatWhole(it, currency) }
            _suggest.update { SuggestState(keeps = keeps, text = rest, fill = s) }
        }
    }

    /** Only the facts' numbers go out; null on any failure, so the device's plan is used. */
    private suspend fun askAi(facts: BudgetFacts, currency: Currency): BudgetSuggestion? = try {
        val (system, user) = budgetPrompt(facts, currency)
        val res = worker.post("/", aiRequest(BUDGET_AI_MODEL, listOf("system" to system, "user" to user), maxTokens = 400, temperature = 0.2))
        if (res.ok) checkReply(readReply(aiContent(res.body)), facts) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun writeBudget(b: Budget) {
        watch(settingsRepo.saveBudget(b), "Couldn't save the budget to your account. Check your connection and try again.")
        _budgetOpen.update { false }
    }

    // ── Goals ──

    private val _goal = MutableStateFlow<GoalEditing?>(null)
    val goal: StateFlow<GoalEditing?> = _goal.asStateFlow()
    private val _goalError = MutableStateFlow<String?>(null)
    val goalError: StateFlow<String?> = _goalError.asStateFlow()

    private fun saved(id: String) = settings.value.goals.find { it.id == id }

    fun newGoal() {
        if (settings.value.goals.size >= MAX_GOALS) {
            return _notice.update { Notice("You can keep up to $MAX_GOALS goals. Delete one you've reached first.") }
        }
        openGoal(GoalEditing(adding = false, goal = null))
    }
    fun editGoal(id: String) = saved(id)?.let { openGoal(GoalEditing(adding = false, goal = it)) }
    fun addMoney(id: String) = saved(id)?.let { openGoal(GoalEditing(adding = true, goal = it)) }
    private fun openGoal(e: GoalEditing) { _goalError.update { null }; _goal.update { e } }
    fun closeGoal() = _goal.update { null }

    fun submitGoal(name: String, target: String, by: String, savedText: String, amount: String) {
        val editing = _goal.value ?: return
        val goals = settings.value.goals
        val check = if (editing.adding) addToGoal(goals, editing.goal!!, amount)
        else saveGoal(goals, editing.goal, name, target, by, savedText, newGoalId())
        when (check) {
            is Checked.Invalid -> _goalError.update { check.message }
            is Checked.Ok -> writeGoals(check.value)
        }
    }

    fun deleteGoal() {
        val g = _goal.value?.goal ?: return
        writeGoals(GoalsChange(settings.value.goals.filter { it.id != g.id }, null))
    }

    private fun writeGoals(change: GoalsChange) {
        watch(settingsRepo.saveGoals(change.goals), "Couldn't save the goal to your account. Check your connection and try again.")
        _goal.update { null }
        change.completed?.let { id -> change.goals.find { it.id == id } }?.let { _notice.update { _ -> Notice("You reached ${it.name}. Well done!") } }
    }

    /** Writes land in the local cache at once; only a refusal from the server is reported, later. */
    private fun watch(write: PendingWrite, failure: String) = viewModelScope.launch {
        try {
            write.confirmed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _notice.update { Notice(failure, error = true) }
        }
    }
}
