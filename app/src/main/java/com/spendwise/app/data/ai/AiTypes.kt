package com.spendwise.app.data.ai

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Each AI feature. A provider needs a vision model for [RECEIPT]. */
enum class AiTask(val needsVision: Boolean) { ASK(false), COACH(false), VOICE(false), RECEIPT(true), CSV(false), BUDGET(false) }

/** One chat completion. [messages] are (role, content); content can be a list of parts, as a receipt's is. */
data class AiCall(
    val task: AiTask,
    val messages: List<Pair<String, JsonElement>>,
    val maxTokens: Int,
    val temperature: Double,
    val topP: Double? = null,
    val readTimeoutMs: Int? = null,
)

sealed interface AiError {
    data object Unauthorized : AiError
    data object RateLimited : AiError
    data object ModelNotFound : AiError
    data object Busy : AiError
    data object Network : AiError
    data object Timeout : AiError
    data object BadReply : AiError
    data object Blocked : AiError
    data object NoConsent : AiError
    data object NoVisionModel : AiError
    data object KeyUnreadable : AiError
    data class Http(val status: Int) : AiError
}

sealed interface AiResult {
    data class Ok(val content: String) : AiResult
    /** [status] is the HTTP status when there was one; CSV import tells 429 and 503 apart by it. */
    data class Failed(val error: AiError, val status: Int? = null) : AiResult
}

/** Plain-text (role, content) messages, as most tasks send them. */
fun textMessages(messages: List<Pair<String, String>>): List<Pair<String, JsonElement>> = messages.map { (role, content) -> role to JsonPrimitive(content) }

/** BYOK plan §8: what the user reads when their own provider fails. Never the provider's body or headers. */
fun byokErrorText(e: AiError): String = when (e) {
    AiError.Unauthorized -> "API key rejected. Check the key."
    AiError.RateLimited -> "Provider limit reached. Check your provider account or try later."
    AiError.ModelNotFound -> "Model unavailable. Check the model ID."
    AiError.Busy -> "Provider is busy. Try again shortly."
    AiError.Network -> "Could not connect. Check your internet."
    AiError.Timeout -> "Provider took too long. Try again."
    AiError.BadReply -> "Provider returned an unsupported response."
    AiError.Blocked -> "Provider could not complete this request."
    AiError.KeyUnreadable -> "Your saved key couldn't be read, so it was removed. SpendWise AI is in use again; add your key to switch back."
    AiError.NoVisionModel -> "Receipt scan needs a vision model; set one in Settings."
    AiError.NoConsent -> "Agree to send requests to your provider first, in Settings › AI provider."
    is AiError.Http -> "Provider error (${e.status}). Try again later."
}
