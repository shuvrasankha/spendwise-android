package com.spendwise.app.ui.insights

import com.spendwise.app.data.worker.AiConsent
import com.spendwise.app.data.worker.WorkerClient
import com.spendwise.app.data.worker.aiContent
import com.spendwise.app.data.worker.aiRequest
import com.spendwise.app.domain.ASK_AI_MODEL
import com.spendwise.app.domain.askPrompt
import com.spendwise.app.domain.readSpec
import com.spendwise.app.domain.todayStr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.DebtRepository
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.BudgetReport
import com.spendwise.app.domain.Checked
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Forecast
import com.spendwise.app.domain.InsightPeriod
import com.spendwise.app.domain.InsightSummary
import com.spendwise.app.domain.PeriodRange
import com.spendwise.app.domain.PieSlice
import com.spendwise.app.domain.budgetReport
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.forecast
import com.spendwise.app.domain.periodRange
import com.spendwise.app.domain.pieSlices
import com.spendwise.app.domain.summarize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import com.spendwise.app.domain.ASK_MAX
import com.spendwise.app.domain.AskResult
import com.spendwise.app.domain.AskSpec
import com.spendwise.app.domain.guessSpec
import com.spendwise.app.domain.runQuery
import javax.inject.Inject

/** The period tabs; Custom starts on this month, like the web's date inputs. */
data class PeriodChoice(
    val period: InsightPeriod = InsightPeriod.MONTH,
    val customStart: String = LocalDate.now().withDayOfMonth(1).toString(),
    val customEnd: String = LocalDate.now().toString(),
)

/** One period's cards: [range] is null when Custom has an error ([periodError]). */
data class Glance(val range: PeriodRange, val summary: InsightSummary, val budget: BudgetReport?, val pie: List<PieSlice>)

/** "Ask about your spending": the spec read from the question, and what it found (ask-data.js). */
data class AskView(val spec: AskSpec, val result: AskResult, val today: String)

sealed interface InsightsState {
    data object Loading : InsightsState
    data class Failed(val message: String) : InsightsState
    /** [ask]: the answer to the last question asked, kept up to date as entries change. */
    data class Ready(val choice: PeriodChoice, val forecast: Forecast, val glance: Glance?, val periodError: String?, val ask: AskView? = null) : InsightsState
}

private data class Records(
    val expenses: List<com.spendwise.app.domain.Expense>,
    val income: List<com.spendwise.app.domain.Income>,
    val debts: List<com.spendwise.app.domain.Debt>,
    val settings: com.spendwise.app.domain.UserSettings,
    val skipped: Set<String>,
)

/** Insights (spec §1.7, §6.9): Ask, the forecast, then the chosen period's cards. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    expenseRepo: ExpenseRepository,
    incomeRepo: IncomeRepository,
    debtRepo: DebtRepository,
    settingsRepo: SettingsRepository,
    prefs: DevicePrefs,
    private val worker: WorkerClient,
    private val consent: AiConsent,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val choice = MutableStateFlow(PeriodChoice())
    private val spec = MutableStateFlow<AskSpec?>(null)
    private val _asking = MutableStateFlow(false)
    /** The question is being read (by the AI, or on the device). */
    val asking: StateFlow<Boolean> = _asking.asStateFlow()

    /** The AI dialog is up, for Ask or the coach. */
    val consentAsked: StateFlow<Boolean> = consent.pending.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun answerConsent(ok: Boolean) = consent.answer(ok)

    val state: StateFlow<InsightsState> = reload.flatMapLatest {
        val records = combine(
            // The web loads them in Firestore's order (by document id); equal amounts keep that order
            expenseRepo.expenses().map { list -> list.sortedBy { it.id } },
            incomeRepo.income(),
            debtRepo.debts().map { list -> list.filter { !it.shared } }, // the web reads only your own
            settingsRepo.settings(),
            prefs.skippedBills,
            ::Records,
        )
        combine(records, choice, spec) { r, c, q -> ready(r, c, q) }
            .flowOn(Dispatchers.Default)
            .onStart<InsightsState> { emit(InsightsState.Loading) }
            .catch { emit(InsightsState.Failed("Couldn't load your data. Check your connection and try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState.Loading)

    val currency: StateFlow<Currency> = prefs.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    fun retry() = reload.update { it + 1 }
    fun select(period: InsightPeriod) = choice.update { it.copy(period = period) }
    fun setCustomStart(date: String) = choice.update { it.copy(customStart = date) }
    fun setCustomEnd(date: String) = choice.update { it.copy(customEnd = date) }

    /** askQuestion: trimmed and cut to 200 characters; an empty one, or one while asking, is ignored. */
    fun ask(text: String) {
        val q = text.trim().take(ASK_MAX)
        if (q.isEmpty() || _asking.value) return
        _asking.value = true
        viewModelScope.launch {
            try {
                spec.value = specFor(q, todayStr())
            } finally {
                _asking.value = false
            }
        }
    }

    /** specFor: the AI reads the question (it gets the question and today's date only); declined, offline or failed, guessSpec does. */
    private suspend fun specFor(question: String, today: String): AskSpec {
        if (consent.ensure()) {
            try {
                val res = worker.post("/", aiRequest(ASK_AI_MODEL, listOf("system" to askPrompt(today), "user" to question), maxTokens = 200, temperature = 0.0))
                if (res.ok) readSpec(aiContent(res.body).orEmpty())?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // offline or over the limit: read it here
            }
        }
        return guessSpec(question, today)
    }

    private fun ready(r: Records, c: PeriodChoice, q: AskSpec?): InsightsState {
        val today = LocalDate.now()
        val todayS = today.toString()
        val ask = q?.let { AskView(it, runQuery(it, r.expenses, r.income), todayS) }
        val f = forecast(r.expenses, todayS) { key, month -> "$key:$month" in r.skipped }
        return when (val range = periodRange(c.period, today, c.customStart, c.customEnd)) {
            is Checked.Invalid -> InsightsState.Ready(c, f, null, range.message, ask)
            is Checked.Ok -> {
                val s = summarize(r.expenses, r.income, r.debts, range.value)
                val glance = Glance(range.value, s, budgetReport(r.expenses, r.settings.budget, range.value, todayS, f.total), pieSlices(s.categories))
                InsightsState.Ready(c, f, glance, null, ask)
            }
        }
    }
}
