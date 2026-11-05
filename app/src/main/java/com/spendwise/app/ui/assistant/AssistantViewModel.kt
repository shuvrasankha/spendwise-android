package com.spendwise.app.ui.assistant

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spendwise.app.data.DebtRepository
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.ai.AiCall
import com.spendwise.app.data.ai.AiError
import com.spendwise.app.data.ai.AiGateway
import com.spendwise.app.data.ai.AiResult
import com.spendwise.app.data.ai.AiTask
import com.spendwise.app.data.ai.textMessages
import com.spendwise.app.data.worker.AiAllowance
import com.spendwise.app.data.worker.AiConsent
import com.spendwise.app.data.worker.NotSignedIn
import com.spendwise.app.domain.ASK_MAX
import com.spendwise.app.domain.ASSISTANT_NO_CONSENT
import com.spendwise.app.domain.AiFailure
import com.spendwise.app.domain.AiFeature
import com.spendwise.app.domain.AssistantContext
import com.spendwise.app.domain.AssistantRoute
import com.spendwise.app.domain.COACH_MAX_TURNS
import com.spendwise.app.domain.COACH_NO_DATA
import com.spendwise.app.domain.COACH_SAVED_MS
import com.spendwise.app.domain.ClarifyOption
import com.spendwise.app.domain.CoachTopic
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.FinCard
import com.spendwise.app.domain.FinQuery
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.adviceFacts
import com.spendwise.app.domain.adviceQuery
import com.spendwise.app.domain.adviceRange
import com.spendwise.app.domain.aiLimitText
import com.spendwise.app.domain.askPrompt
import com.spendwise.app.domain.calculate
import com.spendwise.app.domain.coachErrorText
import com.spendwise.app.domain.coachSystemPrompt
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.mergeAiSpec
import com.spendwise.app.domain.readSpec
import com.spendwise.app.domain.routeQuestion
import com.spendwise.app.domain.summarize
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatWhole
import com.spendwise.app.ui.navigation.Assistant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

enum class TurnKind { USER, CALCULATED, ADVICE, CLARIFY }

/** One message. Answers keep their query, not their figures, so nothing about an entry is stored and totals stay current. */
@Serializable
data class AssistantTurn(
    val kind: TurnKind,
    val text: String = "",
    val query: FinQuery? = null,
    val topic: CoachTopic? = null,
    val options: List<SavedOption> = emptyList(),
)

@Serializable
data class SavedOption(val label: String, val question: String)

/** One conversation per user, kept for a day on this device only, like the coach's before it. */
@Serializable
data class SavedAssistant(val uid: String, val savedAt: Long, val turns: List<AssistantTurn>)

@Immutable
sealed interface AssistantRow {
    @Immutable
    data class Question(val text: String) : AssistantRow
    @Immutable
    data class Calculated(val card: FinCard) : AssistantRow
    /** [text] null: the AI didn't answer, and only the figures are shown. */
    @Immutable
    data class Advice(val card: FinCard?, val text: String?) : AssistantRow
    @Immutable
    data class Clarify(val text: String, val options: List<ClarifyOption>) : AssistantRow
}

data class AssistantUi(
    val rows: List<AssistantRow> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val canRetry: Boolean = false,
    val loadFailed: Boolean = false,
)

private data class Records(val expenses: List<Expense>, val income: List<Income>, val debts: List<Debt>)

/** The one assistant: figures come from the entries on the device, advice from the AI given those figures only. */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    expenseRepo: ExpenseRepository,
    incomeRepo: IncomeRepository,
    debtRepo: DebtRepository,
    private val prefs: DevicePrefs,
    private val ai: AiGateway,
    private val consent: AiConsent,
    private val allowance: AiAllowance,
    private val auth: AuthRepository,
) : ViewModel() {

    private val turns = MutableStateFlow<List<AssistantTurn>>(emptyList())
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<Pair<String, Boolean>?>(null)

    private val records = combine(
        // The web loads them in Firestore's order (by document id); equal amounts keep that order
        expenseRepo.expenses().map { list -> list.sortedBy { it.id } },
        incomeRepo.income(),
        debtRepo.debts().map { list -> list.filter { !it.shared } },
        ::Records,
    )

    val currency: StateFlow<Currency> = prefs.currency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    val state: StateFlow<AssistantUi> = combine(turns, records, prefs.currency, busy, error) { t, r, cur, b, e ->
        AssistantUi(rowsFor(t, r, cur), b, e?.first, e?.second == true)
    }.flowOn(Dispatchers.Default)
        .catch { emit(AssistantUi(loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUi())

    /** The AI dialog is up. */
    val consentAsked: StateFlow<Boolean> = consent.pending.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun answerConsent(ok: Boolean) = consent.answer(ok)

    init {
        val first = savedStateHandle.toRoute<Assistant>().question
        viewModelScope.launch {
            restore()
            if (!first.isNullOrBlank()) send(first)
        }
    }

    fun send(text: String) {
        val q = text.trim().take(ASK_MAX)
        if (q.isEmpty() || busy.value) return
        error.value = null
        turns.update { it + AssistantTurn(TurnKind.USER, q) }
        busy.value = true
        viewModelScope.launch { answer(q) }
    }

    /** Try again: the last question, with whatever it left behind taken off. */
    fun retry() {
        if (busy.value) return
        val i = turns.value.indexOfLast { it.kind == TurnKind.USER }
        if (i < 0) return
        val q = turns.value[i].text
        turns.value = turns.value.take(i + 1)
        error.value = null
        busy.value = true
        viewModelScope.launch { answer(q) }
    }

    fun newChat() {
        if (busy.value) return
        turns.value = emptyList()
        error.value = null
        viewModelScope.launch { prefs.setCoachChat(null) }
    }

    private suspend fun answer(question: String) {
        try {
            val today = LocalDate.now().toString()
            when (val route = routeQuestion(question, today, context())) {
                is AssistantRoute.Calculate -> add(AssistantTurn(TurnKind.CALCULATED, query = if (route.unsure) readWithAi(question, route.query, today) else route.query))
                is AssistantRoute.Clarify -> add(AssistantTurn(TurnKind.CLARIFY, route.text, options = route.options.map { SavedOption(it.label, it.question) }))
                is AssistantRoute.Advise -> advise(route, question)
            }
            save()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = "Couldn't load your data. Check your connection and try again." to true
        } finally {
            busy.value = false
        }
    }

    private fun add(turn: AssistantTurn) = turns.update { it + turn }

    private fun context(): AssistantContext {
        val past = turns.value.dropLast(1) // the question itself is already on the list
        val answer = past.lastOrNull { it.kind == TurnKind.CALCULATED || it.kind == TurnKind.ADVICE }
        return AssistantContext(past.lastOrNull { it.kind == TurnKind.CALCULATED }?.query, answer?.kind == TurnKind.ADVICE, answer?.topic)
    }

    /** A question the parser isn't sure of. Over the cap, declined, offline or unreadable, the parser's reading stands. */
    private suspend fun readWithAi(question: String, local: FinQuery, today: String): FinQuery {
        if (!consent.ensure() || !allowance.left(AiFeature.ASK)) return local
        return try {
            val res = ai.complete(AiCall(AiTask.ASK, textMessages(listOf("system" to askPrompt(today), "user" to question)), maxTokens = 200, temperature = 0.0))
            if (res !is AiResult.Ok) return local
            allowance.used(AiFeature.ASK)
            readSpec(res.content)?.let { mergeAiSpec(local, it) } ?: local
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            local
        }
    }

    private suspend fun advise(route: AssistantRoute.Advise, question: String) {
        val r = records.first()
        val today = LocalDate.now()
        val range = adviceRange(route.from, route.to, today)
        val summary = summarize(r.expenses, r.income, r.debts, range)
        if (!summary.hasData) {
            add(AssistantTurn(TurnKind.CLARIFY, COACH_NO_DATA))
            return
        }
        val query = adviceQuery(route, range)
        fun failed(message: String, retry: Boolean = true) {
            add(AssistantTurn(TurnKind.ADVICE, query = query, topic = route.topic))
            error.value = message to retry
        }
        if (!consent.ensure()) return failed(ASSISTANT_NO_CONSENT)
        if (!allowance.left(AiFeature.COACH)) return failed(aiLimitText(AiFeature.COACH), retry = false)
        val failure = try {
            val currency = prefs.currency.first()
            val facts = adviceFacts(summary, range) { formatWhole(it, currency).replaceFirst(' ', ' ') }
            val messages = listOf("system" to coachSystemPrompt(route.topic, facts, currency)) + aiHistory(r, currency, question)
            val res = ai.complete(AiCall(AiTask.COACH, textMessages(messages), maxTokens = 700, temperature = 0.4))
            val content = (res as? AiResult.Ok)?.content?.trim()?.takeIf { it.isNotEmpty() }
            when {
                res is AiResult.Failed && res.status == 429 -> AiFailure.RATE_LIMITED
                res is AiResult.Failed && res.error == AiError.Network -> AiFailure.OFFLINE
                content == null -> AiFailure.OTHER
                else -> {
                    allowance.used(AiFeature.COACH)
                    add(AssistantTurn(TurnKind.ADVICE, content, query, route.topic))
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotSignedIn) {
            AiFailure.NOT_AUTHENTICATED
        } catch (e: IOException) {
            AiFailure.OFFLINE
        } catch (e: Exception) {
            AiFailure.OTHER
        }
        if (failure != null) failed(coachErrorText(failure, resetTime()))
    }

    /** The conversation as the AI sees it: its own words, and only the title and figure of each calculation. */
    private fun aiHistory(r: Records, currency: Currency, question: String): List<Pair<String, String>> {
        val today = LocalDate.now().toString()
        val past = turns.value.mapNotNull { t ->
            when (t.kind) {
                TurnKind.USER -> "user" to t.text
                TurnKind.ADVICE -> t.text.takeIf { it.isNotEmpty() }?.let { "assistant" to it }
                TurnKind.CALCULATED -> t.query?.let { q ->
                    val c = calculate(q, r.expenses, r.income, today) { formatWhole(it, currency) }
                    "assistant" to "Calculated: ${c.title}: ${c.headline}"
                }
                TurnKind.CLARIFY -> null
            }
        }
        return past.takeLast(COACH_MAX_TURNS).ifEmpty { listOf("user" to question) }
    }

    private fun rowsFor(t: List<AssistantTurn>, r: Records, currency: Currency): List<AssistantRow> {
        val today = LocalDate.now().toString()
        val money = { n: Double -> formatAmount(n, currency) }
        return t.map { x ->
            when (x.kind) {
                TurnKind.USER -> AssistantRow.Question(x.text)
                TurnKind.CALCULATED -> AssistantRow.Calculated(calculate(x.query!!, r.expenses, r.income, today, money))
                TurnKind.ADVICE -> AssistantRow.Advice(x.query?.let { calculate(it, r.expenses, r.income, today, money) }, x.text.takeIf { it.isNotEmpty() })
                TurnKind.CLARIFY -> AssistantRow.Clarify(x.text, x.options.map { ClarifyOption(it.label, it.question) })
            }
        }
    }

    private suspend fun save() {
        val uid = auth.currentUid ?: return
        prefs.setCoachChat(Json.encodeToString(SavedAssistant.serializer(), SavedAssistant(uid, System.currentTimeMillis(), turns.value)))
    }

    /** Today's conversation for this user, if under a day old. A chat saved by an older version doesn't read, and is dropped. */
    private suspend fun restore() {
        val json = prefs.coachChat.first() ?: return
        val saved = runCatching { Json.decodeFromString(SavedAssistant.serializer(), json) }.getOrNull() ?: return
        if (saved.uid != auth.currentUid || System.currentTimeMillis() - saved.savedAt > COACH_SAVED_MS) return
        if (turns.value.isEmpty()) turns.value = saved.turns
    }

    /** aiResetTime: the daily limit resets at midnight UTC, shown in the device's time. */
    private fun resetTime(): String =
        LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}
