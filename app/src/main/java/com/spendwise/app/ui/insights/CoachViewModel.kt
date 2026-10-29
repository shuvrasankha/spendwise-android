package com.spendwise.app.ui.insights

import com.spendwise.app.data.worker.AiAllowance
import com.spendwise.app.domain.AiFeature
import com.spendwise.app.domain.aiLimitText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.worker.AiConsent
import com.spendwise.app.data.worker.NotSignedIn
import com.spendwise.app.data.worker.WorkerClient
import com.spendwise.app.data.worker.aiContent
import com.spendwise.app.data.worker.aiRequest
import com.spendwise.app.domain.AiFailure
import com.spendwise.app.domain.COACH_AI_MODEL
import com.spendwise.app.domain.COACH_MAX_TURNS
import com.spendwise.app.domain.COACH_SAVED_MS
import com.spendwise.app.domain.ChatTurn
import com.spendwise.app.domain.CoachTopic
import com.spendwise.app.domain.InsightSummary
import com.spendwise.app.domain.PeriodRange
import com.spendwise.app.domain.SavedChat
import com.spendwise.app.domain.coachErrorText
import com.spendwise.app.domain.coachSystemPrompt
import com.spendwise.app.domain.factsText
import com.spendwise.app.ui.format.formatWhole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** An open conversation: [label] is the period it's about; [history] ends on a user turn while unanswered. */
data class CoachChat(val topic: CoachTopic, val label: String, val facts: String, val history: List<ChatTurn>)

data class CoachState(val chat: CoachChat? = null, val busy: Boolean = false, val error: String? = null)

/** The AI coach (insights-v2.js): topic chips, then a chat about the period's numbers. Only facts go out. */
@HiltViewModel
class CoachViewModel @Inject constructor(
    private val worker: WorkerClient,
    private val prefs: DevicePrefs,
    private val consent: AiConsent,
    private val auth: AuthRepository,
    private val allowance: AiAllowance,
) : ViewModel() {

    private val _state = MutableStateFlow(CoachState())
    val state: StateFlow<CoachState> = _state.asStateFlow()

    init {
        viewModelScope.launch { restore() }
    }

    fun start(topic: CoachTopic, summary: InsightSummary, range: PeriodRange) {
        if (_state.value.busy || !summary.hasData) return
        viewModelScope.launch {
            if (!consent.ensure()) return@launch
            val currency = prefs.currency.first()
            val facts = factsText(summary, range) { formatWhole(it, currency).replaceFirst(' ', ' ') }
            _state.value = CoachState(CoachChat(topic, range.label, facts, listOf(ChatTurn("user", topic.question))))
            answer()
        }
    }

    fun send(question: String) {
        val q = question.trim()
        val chat = _state.value.chat
        if (_state.value.busy || chat == null || q.isEmpty()) return
        // an unanswered (failed) question is dropped for the new one
        val history = (if (chat.history.lastOrNull()?.role == "user") chat.history.dropLast(1) else chat.history) + ChatTurn("user", q)
        _state.value = CoachState(chat.copy(history = history))
        viewModelScope.launch { answer() }
    }

    fun retry() {
        if (_state.value.busy || _state.value.chat == null) return
        _state.update { it.copy(error = null) }
        viewModelScope.launch { answer() }
    }

    /** closeChat: back to the topics; the saved conversation goes too. */
    fun close() {
        if (_state.value.busy) return
        _state.value = CoachState()
        viewModelScope.launch { prefs.setCoachChat(null) }
    }

    private suspend fun answer() {
        val chat = _state.value.chat ?: return
        if (!allowance.left(AiFeature.COACH)) return _state.update { it.copy(error = aiLimitText(AiFeature.COACH)) }
        _state.update { it.copy(busy = true, error = null) }
        val failure = try {
            val currency = prefs.currency.first()
            val messages = listOf("system" to coachSystemPrompt(chat.topic, chat.facts, currency)) +
                chat.history.takeLast(COACH_MAX_TURNS).map { it.role to it.content }
            val res = worker.post("/", aiRequest(COACH_AI_MODEL, messages, maxTokens = 700, temperature = 0.4))
            val content = if (res.ok) aiContent(res.body)?.trim()?.takeIf { it.isNotEmpty() } else null
            when {
                res.status == 429 -> AiFailure.RATE_LIMITED
                content == null -> AiFailure.OTHER
                else -> {
                    allowance.used(AiFeature.COACH)
                    val done = chat.copy(history = chat.history + ChatTurn("assistant", content))
                    _state.value = CoachState(done)
                    save(done)
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
        // the unanswered question stays in history so Try again can resend it
        if (failure != null) _state.update { it.copy(busy = false, error = coachErrorText(failure, resetTime())) }
    }

    private suspend fun save(chat: CoachChat) {
        val uid = auth.currentUid ?: return
        prefs.setCoachChat(Json.encodeToString(SavedChat.serializer(), SavedChat(uid, System.currentTimeMillis(), chat.topic.name, chat.label, chat.facts, chat.history)))
    }

    /** restoreChat: today's conversation for this user, if under a day old. */
    private suspend fun restore() {
        val json = prefs.coachChat.first() ?: return
        val saved = runCatching { Json.decodeFromString(SavedChat.serializer(), json) }.getOrNull() ?: return
        val topic = CoachTopic.entries.find { it.name == saved.topic } ?: return
        if (saved.uid != auth.currentUid || System.currentTimeMillis() - saved.savedAt > COACH_SAVED_MS) return
        if (_state.value.chat == null) _state.value = CoachState(CoachChat(topic, saved.label, saved.facts, saved.history))
    }

    /** aiResetTime: the daily limit resets at midnight UTC, shown in the device's time. */
    private fun resetTime(): String =
        LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}
