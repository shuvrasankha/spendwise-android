package com.spendwise.app.data.ai

import com.spendwise.app.data.worker.aiContent
import com.spendwise.app.data.worker.aiRequestOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

// Checked 2026-11-05 against https://docs.api.nvidia.com/nim/reference/llm-apis (BYOK plan §2)
const val NVIDIA_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
const val NVIDIA_TEXT_MODEL = "meta/llama-3.3-70b-instruct"
const val NVIDIA_JSON_MODEL = "qwen/qwen2.5-coder-32b-instruct"

/**
 * The shipped model per task. Receipt has none: no hosted vision model is confirmed in NVIDIA's docs
 * yet, so it needs the user's own vision model until one passes the real-key check (steps 3.7).
 */
fun nvidiaDefaultModel(task: AiTask): String? = when (task) {
    AiTask.ASK, AiTask.COACH, AiTask.BUDGET -> NVIDIA_TEXT_MODEL
    AiTask.VOICE, AiTask.CSV -> NVIDIA_JSON_MODEL // holds strict JSON, as on the Worker
    AiTask.RECEIPT -> null
}

private const val READ_TIMEOUT_MS = 30_000

/** An error reply as an [AiError]. The body only steers the mapping; it never reaches a message. */
fun mapProviderError(status: Int, body: String): AiError = when {
    status == 401 || status == 403 -> AiError.Unauthorized
    status == 404 || (status == 400 && body.contains("model", ignoreCase = true)) -> AiError.ModelNotFound
    status == 429 -> AiError.RateLimited
    status == 503 -> AiError.Busy
    else -> AiError.Http(status)
}

private fun finishReason(body: String): String? = runCatching {
    Json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("finish_reason")?.jsonPrimitive?.contentOrNull
}.getOrNull()

/** NVIDIA API Catalog (hosted NIM): OpenAI-style chat completions, the same body the Worker takes. */
@Singleton
class NvidiaProvider @Inject constructor(private val http: AiHttp) {
    suspend fun complete(key: String, model: String, call: AiCall): AiResult {
        val reply = try {
            http.post(
                NVIDIA_URL,
                mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json", "Accept" to "application/json"),
                aiRequestOf(model, call.messages, call.maxTokens, call.temperature, call.topP).toString(),
                call.readTimeoutMs ?: READ_TIMEOUT_MS,
            )
        } catch (e: SocketTimeoutException) {
            return AiResult.Failed(AiError.Timeout)
        } catch (e: IOException) {
            return AiResult.Failed(AiError.Network)
        }
        if (!reply.ok) return AiResult.Failed(mapProviderError(reply.status, reply.body), reply.status)
        if (finishReason(reply.body) == "content_filter") return AiResult.Failed(AiError.Blocked, reply.status)
        return aiContent(reply.body)?.takeIf { it.isNotEmpty() }?.let { AiResult.Ok(it) } ?: AiResult.Failed(AiError.BadReply, reply.status)
    }

    /** One token: enough to prove the key and model work. Any 2xx passes, even one with no text. */
    suspend fun testConnection(key: String, model: String): AiResult =
        complete(key, model, AiCall(AiTask.ASK, textMessages(listOf("user" to "Hi")), maxTokens = 1, temperature = 0.0)).let {
            if (it is AiResult.Failed && it.error == AiError.BadReply) AiResult.Ok("") else it
        }
}
