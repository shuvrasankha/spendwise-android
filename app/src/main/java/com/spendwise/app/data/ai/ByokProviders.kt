package com.spendwise.app.data.ai

import com.spendwise.app.data.worker.aiContent
import com.spendwise.app.data.worker.aiRequestOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/** A provider the user can bring a key for. [keyHint] is the field placeholder, [keyHelp] where to get one. */
data class ByokProvider(val id: String, val name: String, val url: String, val defaultModel: String, val keyHint: String, val keyHelp: String)

const val NVIDIA_URL = "https://integrate.api.nvidia.com/v1/chat/completions"

/**
 * Tested 2026-11-05 with a real key on all six tasks' prompts and parsers (steps 3.7). The models in
 * NVIDIA's docs (llama-3.3-70b, qwen2.5-coder) now answer 410 Gone. This one reads text and images.
 */
const val NVIDIA_DEFAULT_MODEL = "nvidia/nemotron-3.5-super-vl-120b-a12b"

// Checked against each provider's docs on 2026-11-05; every default reads text and images. Not live-tested.
val NVIDIA = ByokProvider("nvidia", "NVIDIA", NVIDIA_URL, NVIDIA_DEFAULT_MODEL, "nvapi-…", "Get a key at build.nvidia.com.")
val OPENAI = ByokProvider("openai", "OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-6-luna", "sk-…", "Get a key at platform.openai.com.")
val GEMINI = ByokProvider(
    "gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-3.8-flash",
    "AIza…", "Get a key at aistudio.google.com.",
)
val ANTHROPIC = ByokProvider("anthropic", "Anthropic", "https://api.anthropic.com/v1/messages", "claude-opus-5-5", "sk-ant-…", "Get a key at platform.claude.com.")

/** OpenAI-compatible, so it takes the plain body. Live-tested 2026-11-06: qwen answers text with no hidden reasoning and takes images; gpt-oss refuses images. */
val GROQ = ByokProvider("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions", "qwen/qwen3.8-27b", "gsk_…", "Get a key at console.groq.com.")

val BYOK_PROVIDER_LIST = listOf(NVIDIA, OPENAI, GEMINI, ANTHROPIC, GROQ)

fun byokProvider(id: String?): ByokProvider = BYOK_PROVIDER_LIST.firstOrNull { it.id == id } ?: NVIDIA

internal const val READ_TIMEOUT_MS = 30_000

/** Reasoning models spend hidden thinking from the same max-tokens budget; without room Ask (200) comes back empty. */
internal const val THINKING_HEADROOM = 2048

private const val ANTHROPIC_VERSION = "2026-10-21"
private const val ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01"

/** An error reply as an [AiError]. The body only steers the mapping; it never reaches a message. */
fun mapProviderError(status: Int, body: String): AiError = when {
    status == 401 || status == 403 -> AiError.Unauthorized
    // Gemini answers a bad key with a 400
    status == 400 && (body.contains("API key", ignoreCase = true) || body.contains("API_KEY_INVALID")) -> AiError.Unauthorized
    status == 404 || status == 410 || (status == 400 && body.contains("model", ignoreCase = true)) -> AiError.ModelNotFound
    status == 429 -> AiError.RateLimited
    status == 503 || status == 529 -> AiError.Busy // 529: Anthropic overloaded
    else -> AiError.Http(status)
}

/**
 * The Worker's body, plus thinking off for the default model: its hidden reasoning spends the
 * max_tokens budget and leaves Ask (200 tokens) with no answer. Other models may not know the field.
 */
fun nvidiaBody(model: String, call: AiCall): JsonObject {
    val body = aiRequestOf(model, call.messages, call.maxTokens, call.temperature, call.topP)
    if (model != NVIDIA_DEFAULT_MODEL) return body
    return JsonObject(body + ("chat_template_kwargs" to buildJsonObject { put("enable_thinking", JsonPrimitive(false)) }))
}

/**
 * OpenAI's current models all reason: max_tokens is deprecated for max_completion_tokens (which counts
 * the reasoning), and temperature/top_p aren't sent since some reasoning models refuse them.
 * Only the default gets reasoning off; not every model accepts "none".
 */
fun openAiBody(model: String, call: AiCall): JsonObject = buildJsonObject {
    put("model", model)
    putJsonArray("messages") { call.messages.forEach { (role, content) -> addJsonObject { put("role", role); put("content", content) } } }
    put("max_completion_tokens", call.maxTokens + THINKING_HEADROOM)
    if (model == OPENAI.defaultModel) put("reasoning_effort", "none")
}

/** Gemini's OpenAI-compatible endpoint. Gemini 3 can't turn thinking off, so the default thinks little. */
fun geminiBody(model: String, call: AiCall): JsonObject {
    val body = aiRequestOf(model, call.messages, call.maxTokens + THINKING_HEADROOM, call.temperature, call.topP)
    return if (model == GEMINI.defaultModel) JsonObject(body + ("reasoning_effort" to JsonPrimitive("low"))) else body
}

/**
 * Anthropic Messages API (not its OpenAI shim). System messages go top-level, image_url data URLs
 * become base64 image blocks. API version 2026-10-21 refuses temperature/top_p, so they aren't sent.
 * Opus 5.5 always thinks: the default runs at low effort, with refusal fallbacks the API reroutes.
 */
fun anthropicBody(model: String, call: AiCall): JsonObject = buildJsonObject {
    put("model", model)
    put("max_tokens", call.maxTokens + THINKING_HEADROOM)
    val system = call.messages.filter { it.first == "system" }.joinToString("\n\n") { it.second.jsonPrimitive.content }
    if (system.isNotEmpty()) put("system", system)
    putJsonArray("messages") {
        call.messages.filter { it.first != "system" }.forEach { (role, content) ->
            addJsonObject { put("role", role); put("content", anthropicContent(content)) }
        }
    }
    if (model == ANTHROPIC.defaultModel) {
        putJsonObject("output_config") { put("effort", "low") }
        put("fallbacks", "default")
    }
}

private fun anthropicContent(content: JsonElement): JsonElement = if (content !is JsonArray) content else buildJsonArray {
    content.forEach { part ->
        val p = part.jsonObject
        val url = p["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content
        if (url == null) { add(p); return@forEach }
        val data = Regex("^data:([^;]+);base64,(.*)$", RegexOption.DOT_MATCHES_ALL).find(url)
        addJsonObject {
            put("type", "image")
            putJsonObject("source") {
                if (data == null) { put("type", "url"); put("url", url) } else {
                    put("type", "base64"); put("media_type", data.groupValues[1]); put("data", data.groupValues[2])
                }
            }
        }
    }
}

internal fun finishReason(body: String): String? = runCatching {
    Json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("finish_reason")?.jsonPrimitive?.contentOrNull
}.getOrNull()

/** (text blocks joined, stop_reason) of a Messages reply; thinking blocks are skipped. */
private fun anthropicReply(body: String): Pair<String?, String?> = runCatching {
    val o = Json.parseToJsonElement(body).jsonObject
    val text = o["content"]?.jsonArray?.map { it.jsonObject }?.filter { it["type"]?.jsonPrimitive?.content == "text" }
        ?.joinToString("") { it["text"]?.jsonPrimitive?.content.orEmpty() }
    text to o["stop_reason"]?.jsonPrimitive?.contentOrNull
}.getOrDefault(null to null)

/** Sends a call to the user's own provider, in that provider's format. */
@Singleton
class ByokClient @Inject constructor(private val http: AiHttp) {
    suspend fun complete(provider: ByokProvider, key: String, model: String, call: AiCall): AiResult {
        val json = mapOf("Content-Type" to "application/json", "Accept" to "application/json")
        val (headers, body) = when (provider.id) {
            ANTHROPIC.id -> json + mapOf("x-api-key" to key, "anthropic-version" to ANTHROPIC_VERSION).let {
                if (model == ANTHROPIC.defaultModel) it + ("anthropic-beta" to ANTHROPIC_FALLBACK_BETA) else it
            } to anthropicBody(model, call)
            OPENAI.id -> mapOf("Authorization" to "Bearer $key") + json to openAiBody(model, call)
            GEMINI.id -> mapOf("Authorization" to "Bearer $key") + json to geminiBody(model, call)
            else -> mapOf("Authorization" to "Bearer $key") + json to nvidiaBody(model, call)
        }
        val reply = try {
            http.post(provider.url, headers, body.toString(), call.readTimeoutMs ?: READ_TIMEOUT_MS)
        } catch (e: SocketTimeoutException) {
            return AiResult.Failed(AiError.Timeout)
        } catch (e: IOException) {
            return AiResult.Failed(AiError.Network)
        }
        if (!reply.ok) return AiResult.Failed(mapProviderError(reply.status, reply.body), reply.status)
        val (text, blocked) = if (provider.id == ANTHROPIC.id) {
            anthropicReply(reply.body).let { (t, stop) -> t to (stop == "refusal") }
        } else {
            aiContent(reply.body) to (finishReason(reply.body) == "content_filter")
        }
        if (blocked) return AiResult.Failed(AiError.Blocked, reply.status)
        return text?.takeIf { it.isNotEmpty() }?.let { AiResult.Ok(it) } ?: AiResult.Failed(AiError.BadReply, reply.status)
    }

    /** One token of answer: enough to prove the key and model work. Any 2xx passes, even one with no text. */
    suspend fun testConnection(provider: ByokProvider, key: String, model: String): AiResult =
        complete(provider, key, model, AiCall(AiTask.ASK, textMessages(listOf("user" to "Hi")), maxTokens = 1, temperature = 0.0)).let {
            if (it is AiResult.Failed && it.error == AiError.BadReply) AiResult.Ok("") else it
        }
}
