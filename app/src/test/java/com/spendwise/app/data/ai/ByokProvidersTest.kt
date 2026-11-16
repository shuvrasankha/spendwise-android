package com.spendwise.app.data.ai

import com.spendwise.app.domain.receiptMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

private const val KEY = "nvapi-TESTSECRET1234"

private class FakeHttp(private val reply: () -> HttpReply) : AiHttp {
    var url = ""
    var headers = emptyMap<String, String>()
    var body = ""
    var timeout = 0
    override suspend fun post(url: String, headers: Map<String, String>, body: String, readTimeoutMs: Int): HttpReply {
        this.url = url; this.headers = headers; this.body = body; timeout = readTimeoutMs
        return reply()
    }
}

class ByokProvidersTest {
    private val ask = AiCall(AiTask.ASK, textMessages(listOf("system" to "s", "user" to "u")), maxTokens = 200, temperature = 0.0)

    private fun send(call: AiCall = ask, reply: () -> HttpReply): Pair<AiResult, FakeHttp> {
        val http = FakeHttp(reply)
        return runBlocking { ByokClient(http).complete(NVIDIA, KEY, "meta/llama-3.3-70b-instruct", call) } to http
    }

    private fun status(code: Int, body: String = "{}") = send { HttpReply(code, body) }.first

    @Test fun `url, headers and body`() {
        val (res, http) = send { HttpReply(200, """{"choices":[{"message":{"content":"hi"},"finish_reason":"stop"}]}""") }
        assertEquals(AiResult.Ok("hi"), res)
        assertEquals("https://integrate.api.nvidia.com/v1/chat/completions", http.url)
        assertEquals(mapOf("Authorization" to "Bearer $KEY", "Content-Type" to "application/json", "Accept" to "application/json"), http.headers)
        assertEquals(
            """{"model":"meta/llama-3.3-70b-instruct","messages":[{"role":"system","content":"s"},{"role":"user","content":"u"}],"max_tokens":200,"temperature":0.0}""",
            http.body,
        )
        assertEquals(30_000, http.timeout)
    }

    @Test fun `a receipt sends its image as an image_url part`() {
        val call = AiCall(AiTask.RECEIPT, receiptMessages("2026-11-05", "data:image/jpeg;base64,AAAA"), maxTokens = 1500, temperature = 0.1, topP = 0.95)
        val http = send(call) { HttpReply(200, "{}") }.second
        assertTrue(http.body.contains("""{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,AAAA"}}"""))
        assertTrue(http.body.endsWith(""""max_tokens":1500,"temperature":0.1,"top_p":0.95}"""))
    }

    @Test fun `empty choices or non-JSON is BadReply`() {
        assertEquals(AiResult.Failed(AiError.BadReply, 200), status(200, """{"choices":[]}"""))
        assertEquals(AiResult.Failed(AiError.BadReply, 200), status(200, "<html>"))
        assertEquals(AiResult.Failed(AiError.BadReply, 200), status(200, """{"choices":[{"message":{"content":""}}]}"""))
    }

    @Test fun `content filter is Blocked`() = assertEquals(
        AiResult.Failed(AiError.Blocked, 200),
        status(200, """{"choices":[{"message":{"content":""},"finish_reason":"content_filter"}]}"""),
    )

    @Test fun `error statuses`() {
        assertEquals(AiResult.Failed(AiError.Unauthorized, 401), status(401))
        assertEquals(AiResult.Failed(AiError.Unauthorized, 403), status(403))
        assertEquals(AiResult.Failed(AiError.ModelNotFound, 404), status(404))
        assertEquals(AiResult.Failed(AiError.ModelNotFound, 410), status(410, """{"title":"Gone","detail":"The model has reached its end of life"}"""))
        assertEquals(AiResult.Failed(AiError.ModelNotFound, 400), status(400, """{"detail":"Model 'x' not found"}"""))
        assertEquals(AiResult.Failed(AiError.Http(400), 400), status(400, """{"detail":"bad max_tokens"}"""))
        assertEquals(AiResult.Failed(AiError.RateLimited, 429), status(429))
        assertEquals(AiResult.Failed(AiError.Busy, 503), status(503))
        assertEquals(AiResult.Failed(AiError.Http(500), 500), status(500))
    }

    @Test fun `timeout and network`() {
        assertEquals(AiResult.Failed(AiError.Timeout), send { throw SocketTimeoutException("read timed out") }.first)
        assertEquals(AiResult.Failed(AiError.Network), send { throw IOException("no route") }.first)
    }

    @Test fun `no result carries the key, even when the server echoes it`() {
        val echoed = """{"error":"bad key $KEY"}"""
        for (code in listOf(200, 400, 401, 429, 500)) assertFalse(status(code, echoed).toString().contains("TESTSECRET"))
    }

    @Test fun `connection test passes on any 2xx, even with no text`() {
        val http = FakeHttp { HttpReply(200, """{"choices":[{"message":{"content":""}}]}""") }
        assertEquals(AiResult.Ok(""), runBlocking { ByokClient(http).testConnection(NVIDIA, KEY, "m") })
        assertTrue(http.body.contains(""""max_tokens":1"""))
        assertEquals(AiResult.Failed(AiError.Unauthorized, 401), runBlocking { ByokClient(FakeHttp { HttpReply(401, "") }).testConnection(NVIDIA, KEY, "m") })
    }

    @Test fun `every provider has a distinct id and an https url`() {
        assertEquals(BYOK_PROVIDER_LIST.size, BYOK_PROVIDER_LIST.map { it.id }.toSet().size)
        assertTrue(BYOK_PROVIDER_LIST.all { it.url.startsWith("https://") })
        assertEquals(NVIDIA, byokProvider(null))
        assertEquals(NVIDIA, byokProvider("gone"))
        assertEquals(ANTHROPIC, byokProvider("anthropic"))
    }

    private fun other(p: ByokProvider, model: String = p.defaultModel, call: AiCall = ask, reply: String = "{}"): Pair<AiResult, FakeHttp> {
        val http = FakeHttp { HttpReply(200, reply) }
        return runBlocking { ByokClient(http).complete(p, KEY, model, call) } to http
    }

    @Test fun `openai sends max_completion_tokens with room to reason, and no sampling fields`() {
        val (res, http) = other(OPENAI, reply = """{"choices":[{"message":{"content":"hi"}}]}""")
        assertEquals(AiResult.Ok("hi"), res)
        assertEquals(OPENAI.url, http.url)
        assertEquals("Bearer $KEY", http.headers["Authorization"])
        assertEquals(
            """{"model":"gpt-6-luna","messages":[{"role":"system","content":"s"},{"role":"user","content":"u"}],"max_completion_tokens":2248,"reasoning_effort":"none"}""",
            http.body,
        )
        assertFalse(other(OPENAI, model = "gpt-6-astra").second.body.contains("reasoning_effort"))
    }

    @Test fun `gemini uses its openai endpoint, and a bad key's 400 is Unauthorized`() {
        val http = other(GEMINI).second
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", http.url)
        assertTrue(http.body.contains(""""max_tokens":2248,"temperature":0.0,"reasoning_effort":"low""""))
        assertEquals(AiError.Unauthorized, mapProviderError(400, """[{"error":{"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}]"""))
    }

    @Test fun `anthropic uses the messages api, top-level system, and its own headers`() {
        val (res, http) = other(ANTHROPIC, reply = """{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"h"},{"type":"text","text":"i"}],"stop_reason":"end_turn"}""")
        assertEquals(AiResult.Ok("hi"), res)
        assertEquals("https://api.anthropic.com/v1/messages", http.url)
        assertEquals(KEY, http.headers["x-api-key"])
        assertEquals("2026-10-21", http.headers["anthropic-version"])
        assertEquals("server-side-fallback-2026-07-01", http.headers["anthropic-beta"])
        assertFalse(http.headers.containsKey("Authorization"))
        assertEquals(
            """{"model":"claude-opus-5-5","max_tokens":2248,"system":"s","messages":[{"role":"user","content":"u"}],"output_config":{"effort":"low"},"fallbacks":"default"}""",
            http.body,
        )
        val haiku = other(ANTHROPIC, model = "claude-haiku-4-5").second
        assertFalse(haiku.body.contains("effort") || haiku.body.contains("fallbacks") || haiku.headers.containsKey("anthropic-beta"))
    }

    @Test fun `anthropic gets the receipt as a base64 image block`() {
        val call = AiCall(AiTask.RECEIPT, receiptMessages("2026-11-05", "data:image/jpeg;base64,AAAA"), maxTokens = 1500, temperature = 0.1, topP = 0.95)
        val body = other(ANTHROPIC, call = call).second.body
        assertTrue(body.contains("""{"type":"text","text":"Read every receipt or bill in this photo."},{"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":"AAAA"}}"""))
        assertFalse(body.contains("temperature") || body.contains("top_p") || body.contains("image_url"))
    }

    @Test fun `anthropic refusal is Blocked, no text is BadReply, 529 is Busy`() {
        assertEquals(AiResult.Failed(AiError.Blocked, 200), other(ANTHROPIC, reply = """{"content":[],"stop_reason":"refusal"}""").first)
        assertEquals(AiResult.Failed(AiError.BadReply, 200), other(ANTHROPIC, reply = """{"content":[{"type":"thinking","thinking":""}],"stop_reason":"max_tokens"}""").first)
        assertEquals(AiError.Busy, mapProviderError(529, "{}"))
    }

    @Test fun `thinking is off for the default model only`() {
        assertTrue(nvidiaBody(NVIDIA_DEFAULT_MODEL, ask).toString().endsWith(""""temperature":0.0,"chat_template_kwargs":{"enable_thinking":false}}"""))
        assertFalse(nvidiaBody("someone/else", ask).toString().contains("chat_template_kwargs"))
    }
}

class UrlConnectionHttpTest {
    @Test(expected = IllegalArgumentException::class)
    fun `refuses plain http`() {
        runBlocking { UrlConnectionHttp().post("http://integrate.api.nvidia.com/v1/chat/completions", emptyMap(), "{}", 1000) }
    }

    /** A server that never answers: only disconnect() can end the read before its 60 s timeout. */
    @Test fun `cancelling disconnects a hung request`() {
        ServerSocket(0).use { server ->
            val held = thread { runCatching { server.accept().use { Thread.sleep(10_000) } } }
            val url = "https://127.0.0.1:${server.localPort}/v1/chat/completions"
            runBlocking {
                val call = async(Dispatchers.IO) { runCatching { UrlConnectionHttp().post(url, emptyMap(), "{}", 60_000) } }
                delay(300)
                withTimeout(5_000) { call.cancel(); call.join() }
            }
            held.interrupt()
        }
    }
}
