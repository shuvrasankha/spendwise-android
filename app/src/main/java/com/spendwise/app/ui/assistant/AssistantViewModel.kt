package com.spendwise.app.ui.assistant

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spendwise.app.data.HoldingRepository
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
import com.spendwise.app.data.ai.byokErrorText
import com.spendwise.app.data.ai.textMessages
import com.spendwise.app.data.worker.AiAllowance
import com.spendwise.app.data.worker.AiConsent
import com.spendwise.app.data.worker.NotSignedIn
import com.spendwise.app.domain.calculatedFacts
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.investFacts
import com.spendwise.app.domain.portfolio
import com.spendwise.app.domain.ASK_MAX
import com.spendwise.app.domain.ASSISTANT_NO_CONSENT
import com.spendwise.app.domain.AiFailure
import com.spendwise.app.domain.AiFeature
import com.spendwise.app.domain.AskSpec
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
import com.spendwise.app.domain.remindFollowUps
import com.spendwise.app.domain.splitFollowUps
import com.spendwise.app.domain.withFollowUps
import com.spendwise.app.domain.runQuery
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
import com.spendwise.app.domain.debtFacts
import com.spendwise.app.domain.BILL_QUESTION
import com.spendwise.app.domain.RECEIPT_BAD_PHOTO
import com.spendwise.app.domain.billMessages
import com.spendwise.app.data.ReceiptPhotos
import android.net.Uri

enum class TurnKind { USER, CALCULATED, ADVICE, CLARIFY }

/** One message. Answers keep their query, not their figures, so nothing about an entry is stored and totals stay current. */
@Serializable
data class AssistantTurn(
    val kind: TurnKind,
    val text: String = "",
    val query: FinQuery? = null,
    val topic: CoachTopic? = null,
    val options: List<SavedOption> = emptyList(),
    /** The AI that wrote an ADVICE answer; empty for chats saved before this was kept. */
    val by: String = "",
    /** A USER question sent with a photo; the photo itself is never kept. */
    val photo: Boolean = false,
)

@Serializable
data class SavedOption(val label: String, val question: String)

/** One conversation, on this device only. [id] is when it started. */
@Serializable
data class SavedChat(val id: Long, val savedAt: Long, val turns: List<AssistantTurn>) {
    val title get() = turns.firstOrNull { it.kind == TurnKind.USER }?.text.orEmpty()
}

/** The user's past chats, newest first. Another user's on this device are not shown. */
@Serializable
data class SavedChats(val uid: String, val chats: List<SavedChat>)

const val CHATS_KEPT = 30

/** [chat] replaces the one with its id, moves to the top, and the oldest past [CHATS_KEPT] drop off. */
fun upsertChat(chats: List<SavedChat>, chat: SavedChat): List<SavedChat> =
    (listOf(chat) + chats.filter { it.id != chat.id }).take(CHATS_KEPT)

@Immutable
sealed interface AssistantRow {
    @Immutable
    data class Question(val text: String, val photo: Boolean = false) : AssistantRow
    @Immutable
    data class Calculated(val card: FinCard) : AssistantRow
    /** [text] null: the AI didn't answer, and only the figures are shown. */
    @Immutable
    data class Advice(val card: FinCard?, val text: String?, val by: String = "", val next: List<String> = emptyList()) : AssistantRow
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

private data class Records(val expenses: List<Expense>, val income: List<Income>, val debts: List<Debt>, val holdings: List<Holding>)

/** The one assistant: figures come from the entries on the device, advice from the AI given those figures only. */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    expenseRepo: ExpenseRepository,
    incomeRepo: IncomeRepository,
    debtRepo: DebtRepository,
    holdingRepo: HoldingRepository,
    private val prefs: DevicePrefs,
    private val ai: AiGateway,
    private val consent: AiConsent,
    private val allowance: AiAllowance,
    private val auth: AuthRepository,
    private val photos: ReceiptPhotos,
) : ViewModel() {

    private val turns = MutableStateFlow<List<AssistantTurn>>(emptyList())
    private var chatId = System.currentTimeMillis()

    /** Past chats on this device, newest first. */
    val chats: StateFlow<List<SavedChat>> = prefs.coachChat.map { json -> readChats(json) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun readChats(json: String?): List<SavedChat> {
        val saved = json?.let { runCatching { Json.decodeFromString(SavedChats.serializer(), it) }.getOrNull() } ?: return emptyList()
        return if (saved.uid == auth.currentUid) saved.chats else emptyList()
    }
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<Pair<String, Boolean>?>(null)

    private val records = combine(
        // The web loads them in Firestore's order (by document id); equal amounts keep that order
        expenseRepo.expenses().map { list -> list.sortedBy { it.id } },
        incomeRepo.income(),
        debtRepo.debts().map { list -> list.filter { !it.shared } },
        holdingRepo.holdings(),
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

    /** Where the camera saves a bill photo. */
    fun newPhotoUri(): Uri = photos.newUri()

    /** A bill or statement photo with [text] as the question: read by the vision model, then deleted. */
    fun sendPhoto(uri: Uri?, text: String) {
        if (uri == null || busy.value) return photos.clear()
        val q = text.trim().take(ASK_MAX).ifEmpty { BILL_QUESTION }
        error.value = null
        turns.update { it + AssistantTurn(TurnKind.USER, q, photo = true) }
        busy.value = true
        viewModelScope.launch {
            try {
                explainPhoto(uri, q)
                save()
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun explainPhoto(uri: Uri, question: String) {
        fun failed(message: String) {
            error.value = message to false // the photo is gone, so a retry has nothing to send
        }
        val image = try {
            photos.dataUrl(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            photos.clear()
        }
        if (image == null) return failed(RECEIPT_BAD_PHOTO)
        if (!consent.ensure()) return failed(ASSISTANT_NO_CONSENT)
        val byok = ai.byok()
        if (!byok && !allowance.left(AiFeature.VOICE)) return failed(aiLimitText(AiFeature.VOICE))
        val failure = try {
            val by = ai.label()
            val res = ai.complete(AiCall(AiTask.RECEIPT, billMessages(prefs.currency.first(), question, image), maxTokens = 700, temperature = 0.3))
            val content = (res as? AiResult.Ok)?.content?.trim()?.takeIf { it.isNotEmpty() }
            when {
                byok && content == null -> return failed(byokErrorText((res as? AiResult.Failed)?.error ?: AiError.BadReply))
                res is AiResult.Failed && res.status == 429 -> AiFailure.RATE_LIMITED
                res is AiResult.Failed && res.error == AiError.Network -> AiFailure.OFFLINE
                content == null -> AiFailure.OTHER
                else -> {
                    if (!byok) allowance.used(AiFeature.VOICE)
                    val (answer, next) = splitFollowUps(content)
                    add(AssistantTurn(TurnKind.ADVICE, answer, options = next.map { SavedOption(it, it) }, by = by))
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

    /** The chat so far is already saved; this starts an empty one. */
    fun newChat() {
        if (busy.value) return
        turns.value = emptyList()
        error.value = null
        chatId = System.currentTimeMillis()
    }

    fun openChat(id: Long) {
        if (busy.value) return
        val chat = chats.value.firstOrNull { it.id == id } ?: return
        chatId = chat.id
        turns.value = chat.turns
        error.value = null
    }

    fun deleteChat(id: Long) = viewModelScope.launch {
        if (id == chatId) newChat()
        writeChats(chats.value.filter { it.id != id })
    }

    private suspend fun writeChats(list: List<SavedChat>) {
        val uid = auth.currentUid ?: return
        prefs.setCoachChat(Json.encodeToString(SavedChats.serializer(), SavedChats(uid, list)))
    }

    private suspend fun answer(question: String) {
        try {
            val today = LocalDate.now().toString()
            val r = records.first()
            val found = { spec: AskSpec -> runQuery(spec, r.expenses, r.income).count > 0 }
            when (val route = routeQuestion(question, today, context(), found)) {
                is AssistantRoute.Calculate -> {
                    val query = if (route.unsure) readWithAi(question, route.query, today) else route.query
                    // The figure is worked out here; the AI explains it, so every question gets a written answer
                    advise(AssistantRoute.Advise(CoachTopic.OVERVIEW, query.from, query.to, query.categories), question, calculated = query)
                }
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
        val byok = ai.byok()
        if (!consent.ensure() || (!byok && !allowance.left(AiFeature.ASK))) return local
        return try {
            val res = ai.complete(AiCall(AiTask.ASK, textMessages(listOf("system" to askPrompt(today), "user" to question)), maxTokens = 200, temperature = 0.0))
            if (res !is AiResult.Ok) return local
            if (!byok) allowance.used(AiFeature.ASK)
            readSpec(res.content)?.let { mergeAiSpec(local, it) } ?: local
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            local
        }
    }

    /**
     * Advice from the AI. With [calculated], a sum already worked out here: its card shows above the AI's words,
     * and when the AI can't answer (no consent yet, today's limit, offline) the card alone is the answer, quietly.
     */
    private suspend fun advise(route: AssistantRoute.Advise, question: String, calculated: FinQuery? = null) {
        val r = records.first()
        val today = LocalDate.now()
        val range = adviceRange(route.from, route.to, today)
        val summary = summarize(r.expenses, r.income, r.debts, range)
        if (calculated == null && !summary.hasData) {
            add(AssistantTurn(TurnKind.CLARIFY, COACH_NO_DATA))
            return
        }
        val query = calculated ?: adviceQuery(route, range)
        fun failed(message: String, retry: Boolean = true) {
            if (calculated != null) return add(AssistantTurn(TurnKind.CALCULATED, query = calculated))
            add(AssistantTurn(TurnKind.ADVICE, query = query, topic = route.topic))
            error.value = message to retry
        }
        val byok = ai.byok()
        // A sum never opens the consent dialog: the user's own key was agreed to when it was turned on
        val agreed = if (calculated != null) byok || prefs.aiConsent.first() else consent.ensure()
        if (!agreed) return failed(ASSISTANT_NO_CONSENT)
        if (!byok && !allowance.left(AiFeature.COACH)) return failed(aiLimitText(AiFeature.COACH), retry = false)
        val failure = try {
            val currency = prefs.currency.first()
            val fmt = { n: Double -> formatWhole(n, currency).replaceFirst(' ', ' ') }
            // Debt advice needs each open debt, not only the totals: amounts and age, never names or notes
            val facts = adviceFacts(summary, range, fmt) + (if (route.topic == CoachTopic.DEBT) "\n" + debtFacts(r.debts, today.toString(), fmt) else "") +
                // Investments are whole-portfolio figures, not the period's: what's held today
                "\n" + investFacts(portfolio(r.holdings, r.expenses, today.toString()), fmt) +
                (calculated?.let { "\n" + calculatedFacts(calculate(it, r.expenses, r.income, today.toString(), fmt)) } ?: "")
            val messages = listOf("system" to coachSystemPrompt(route.topic, facts, currency)) + aiHistory(r, currency, question)
            val by = ai.label()
            val res = ai.complete(AiCall(AiTask.COACH, textMessages(messages), maxTokens = 700, temperature = 0.6))
            val content = (res as? AiResult.Ok)?.content?.trim()?.takeIf { it.isNotEmpty() }
            if (byok && content == null) return failed(byokErrorText((res as? AiResult.Failed)?.error ?: AiError.BadReply))
            when {
                res is AiResult.Failed && res.status == 429 -> AiFailure.RATE_LIMITED
                res is AiResult.Failed && res.error == AiError.Network -> AiFailure.OFFLINE
                content == null -> AiFailure.OTHER
                else -> {
                    if (!byok) allowance.used(AiFeature.COACH)
                    val (answer, next) = splitFollowUps(content)
                    add(AssistantTurn(TurnKind.ADVICE, answer, query, route.topic, next.map { SavedOption(it, it) }, by))
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
                // With its follow-ups put back, so past answers show the model the format it's asked for
                TurnKind.ADVICE -> t.text.takeIf { it.isNotEmpty() }?.let { "assistant" to withFollowUps(it, t.options.map { o -> o.question }) }
                TurnKind.CALCULATED -> t.query?.let { q ->
                    val c = calculate(q, r.expenses, r.income, today) { formatWhole(it, currency) }
                    "assistant" to "Calculated: ${c.title}: ${c.headline}"
                }
                TurnKind.CLARIFY -> null
            }
        }
        return remindFollowUps(past.takeLast(COACH_MAX_TURNS).ifEmpty { listOf("user" to question) })
    }

    private fun rowsFor(t: List<AssistantTurn>, r: Records, currency: Currency): List<AssistantRow> {
        val today = LocalDate.now().toString()
        val money = { n: Double -> formatAmount(n, currency) }
        return t.map { x ->
            when (x.kind) {
                TurnKind.USER -> AssistantRow.Question(x.text, x.photo)
                TurnKind.CALCULATED -> AssistantRow.Calculated(calculate(x.query!!, r.expenses, r.income, today, money))
                TurnKind.ADVICE -> AssistantRow.Advice(x.query?.let { calculate(it, r.expenses, r.income, today, money) }, x.text.takeIf { it.isNotEmpty() }, x.by, x.options.map { it.question })
                TurnKind.CLARIFY -> AssistantRow.Clarify(x.text, x.options.map { ClarifyOption(it.label, it.question) })
            }
        }
    }

    private suspend fun save() {
        if (turns.value.isEmpty()) return
        writeChats(upsertChat(readChats(prefs.coachChat.first()), SavedChat(chatId, System.currentTimeMillis(), turns.value)))
    }

    /** The last chat carries on if it's under a day old; older ones stay in the list. A chat saved by an older version doesn't read, and is dropped. */
    private suspend fun restore() {
        val last = readChats(prefs.coachChat.first()).firstOrNull() ?: return
        if (System.currentTimeMillis() - last.savedAt > COACH_SAVED_MS || turns.value.isNotEmpty()) return
        chatId = last.id
        turns.value = last.turns
    }

    /** aiResetTime: the daily limit resets at midnight UTC, shown in the device's time. */
    private fun resetTime(): String =
        LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}
