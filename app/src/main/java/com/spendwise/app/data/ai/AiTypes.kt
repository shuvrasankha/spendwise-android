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

interface AiProvider {
    val id: String
    suspend fun complete(model: String, call: AiCall): AiResult
}

/** Plain-text (role, content) messages, as most tasks send them. */
fun textMessages(messages: List<Pair<String, String>>): List<Pair<String, JsonElement>> = messages.map { (role, content) -> role to JsonPrimitive(content) }
