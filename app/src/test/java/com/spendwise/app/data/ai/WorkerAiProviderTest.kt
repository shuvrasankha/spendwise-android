package com.spendwise.app.data.ai

import com.spendwise.app.data.worker.WorkerResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class WorkerAiProviderTest {
    private val call = AiCall(AiTask.CSV, textMessages(listOf("user" to "u")), maxTokens = 10, temperature = 0.1, readTimeoutMs = 90_000)
    private var sent: Pair<JsonObject, Int?>? = null

    private fun reply(status: Int, body: String) = run(WorkerAiProvider { b, t -> sent = b to t; WorkerResponse(status, body) })
    private fun run(p: WorkerAiProvider) = runBlocking { p.complete("@cf/m", call) }

    @Test fun `200 with content is Ok, sent with the model and timeout`() {
        assertEquals(AiResult.Ok("hi"), reply(200, """{"choices":[{"message":{"content":"hi"}}]}"""))
        assertEquals("""{"model":"@cf/m","messages":[{"role":"user","content":"u"}],"max_tokens":10,"temperature":0.1}""", sent!!.first.toString())
        assertEquals(90_000, sent!!.second)
    }

    @Test fun `200 without content is BadReply`() = assertEquals(AiResult.Failed(AiError.BadReply, 200), reply(200, "{}"))

    @Test fun `429 keeps its status`() = assertEquals(AiResult.Failed(AiError.Http(429), 429), reply(429, "slow down"))

    @Test fun `IOException is Network`() =
        assertEquals(AiResult.Failed(AiError.Network), run(WorkerAiProvider { _, _ -> throw IOException("offline") }))

    @Test fun `worker models are today's constants`() = assertEquals(
        listOf(
            "@cf/meta/llama-3.3-70b-instruct-fp8-fast", "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
            "@cf/qwen/qwen2.5-coder-32b-instruct", "@cf/meta/llama-4-scout-17b-16e-instruct",
            "@cf/qwen/qwen2.5-coder-32b-instruct", "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
        ),
        listOf(AiTask.ASK, AiTask.COACH, AiTask.VOICE, AiTask.RECEIPT, AiTask.CSV, AiTask.BUDGET).map(::workerModel),
    )
}
