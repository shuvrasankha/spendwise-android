package com.spendwise.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.SettingsRepository
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

/** The goal dialog: "add" money to [goal], or edit it ([goal] null for a new one). */
data class GoalEditing(val adding: Boolean, val goal: Goal?)

/** Home's actions: due bills, the budget dialog and the goal dialog (dashboard.js, budget.js, goals.js). */
@HiltViewModel
class PlansViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    private val settingsRepo: SettingsRepository,
    private val prefs: DevicePrefs,
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

    fun openBudget() { _budgetError.update { null }; _budgetOpen.update { true } }
    fun closeBudget() = _budgetOpen.update { false }

    fun saveBudget(totalText: String, cats: Map<String, String>) {
        when (val c = checkBudget(totalText, cats)) {
            is Checked.Invalid -> _budgetError.update { c.message }
            is Checked.Ok -> writeBudget(c.value)
        }
    }

    fun removeBudget() = writeBudget(Budget())

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
