package com.spendwise.app.data.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.worker.WorkerResponse
import com.spendwise.app.domain.mapInParallel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

private const val OK_BODY = """{"choices":[{"message":{"content":"hi"}}]}"""

class AiGatewayTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: DevicePrefs
    private lateinit var keys: ApiKeyStore
    private lateinit var gateway: AiGateway
    private val cipher = object : SecretCipher {
        var broken = false
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(blob: ByteArray) = if (broken) error("lost") else blob.reversedArray()
        override fun destroy() {}
    }
    private var workerCalls = 0
    private val nvidiaBodies = mutableListOf<String>()
    private val urls = mutableListOf<String>()
    private var nvidiaReply = HttpReply(200, OK_BODY)
    private var duringNvidia: suspend () -> Unit = {}

    @Before fun setUp() {
        val file = tmp.newFile("p.preferences_pb").also { it.delete() }
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { file })
        keys = ApiKeyStore(cipher, tmp.newFolder("keys"), prefs)
        val worker = WorkerAiProvider { _, _ -> workerCalls++; WorkerResponse(200, OK_BODY) }
        val http = object : AiHttp {
            override suspend fun post(url: String, headers: Map<String, String>, body: String, readTimeoutMs: Int): HttpReply {
                urls += url
                nvidiaBodies += body
                duringNvidia()
                return nvidiaReply
            }
        }
        gateway = AiGateway(prefs, keys, worker, ByokClient(http))
    }

    @After fun tearDown() = scope.cancel()

    private fun call(task: AiTask = AiTask.ASK) = AiCall(task, textMessages(listOf("user" to "u")), maxTokens = 10, temperature = 0.0)
    private fun complete(task: AiTask = AiTask.ASK) = runBlocking { gateway.complete(call(task)) }

    private fun byokReady(consent: Boolean = true) = runBlocking {
        keys.save("nvidia", "nvapi-TESTSECRET1234".toCharArray())
        prefs.setByokProvider("nvidia")
        prefs.setByokConsent("nvidia", consent)
        prefs.setAiMode("byok")
    }

    private fun model(body: String) = Regex(""""model":"([^"]+)"""").find(body)!!.groupValues[1]

    @Test fun `worker by default, and nvidia is never touched`() {
        assertEquals(AiResult.Ok("hi"), complete())
        assertEquals(1, workerCalls)
        assertTrue(nvidiaBodies.isEmpty())
        assertEquals(false, runBlocking { gateway.byok() })
    }

    @Test fun `byok uses nvidia with the default model and never the worker, even on errors`() {
        byokReady()
        assertEquals(AiResult.Ok("hi"), complete())
        assertEquals(NVIDIA_DEFAULT_MODEL, model(nvidiaBodies.single()))
        for (status in listOf(401, 429, 503)) {
            nvidiaReply = HttpReply(status, "{}")
            assertTrue(complete() is AiResult.Failed)
        }
        assertEquals(0, workerCalls)
    }

    @Test fun `no consent sends nothing`() {
        byokReady(consent = false)
        assertEquals(AiResult.Failed(AiError.NoConsent), complete())
        assertEquals(0, workerCalls)
        assertTrue(nvidiaBodies.isEmpty())
    }

    @Test fun `an unreadable key goes back to the worker mode, and says so`() {
        byokReady()
        cipher.broken = true
        assertEquals(AiResult.Failed(AiError.KeyUnreadable), complete())
        assertEquals("worker", runBlocking { prefs.aiMode.first() })
        assertTrue(nvidiaBodies.isEmpty())
        assertEquals(0, workerCalls) // this call isn't silently re-sent elsewhere
    }

    @Test fun `overrides, text for text tasks and vision for receipts`() {
        byokReady()
        runBlocking {
            prefs.setByokModel("text", "nvidia", "acme/text-1")
            prefs.setByokModel("vision", "nvidia", "acme/vision-1")
        }
        complete(AiTask.CSV)
        complete(AiTask.RECEIPT)
        assertEquals(listOf("acme/text-1", "acme/vision-1"), nvidiaBodies.map(::model))
    }

    @Test fun `the chosen provider gets the call, with its own key and default model`() {
        runBlocking {
            keys.save("anthropic", "sk-ant-TESTSECRET1234".toCharArray())
            prefs.setByokProvider("anthropic")
            prefs.setByokConsent("anthropic", true)
            prefs.setAiMode("byok")
        }
        nvidiaReply = HttpReply(200, """{"content":[{"type":"text","text":"hi"}]}""")
        assertEquals(AiResult.Ok("hi"), complete())
        assertEquals(listOf(ANTHROPIC.url), urls)
        assertEquals(ANTHROPIC.defaultModel, model(nvidiaBodies.single()))
        runBlocking { prefs.setByokConsent("nvidia", true) } // consent for another provider doesn't count
        runBlocking { prefs.setByokConsent("anthropic", false) }
        assertEquals(AiResult.Failed(AiError.NoConsent), complete())
    }

    @Test fun `a call keeps its provider when the mode changes mid-call`() {
        byokReady()
        duringNvidia = { prefs.setAiMode("worker") }
        assertEquals(AiResult.Ok("hi"), complete())
        assertEquals(0, workerCalls)
        duringNvidia = {}
        complete()
        assertEquals(1, workerCalls) // the next call follows the new mode
    }
}

class MapInParallelTest {
    private fun peak(items: Int, max: Int): Pair<Int, List<Int>> {
        val now = AtomicInteger()
        val peak = AtomicInteger()
        val out = runBlocking(Dispatchers.Default) {
            mapInParallel((1..items).toList(), max) { i ->
                peak.accumulateAndGet(now.incrementAndGet(), ::maxOf)
                delay(20)
                now.decrementAndGet()
                i * 10
            }
        }
        return peak.get() to out
    }

    @Test fun `byok csv runs at most two at once, in file order`() {
        val (p, out) = peak(items = 6, max = 2)
        assertTrue("peak $p", p <= 2)
        assertEquals((1..6).map { it * 10 }, out)
    }

    @Test fun `worker csv stays fully parallel`() = assertEquals(6, peak(items = 6, max = 6).first)

    @Test fun `an empty file is fine`() = assertEquals(emptyList<Int>(), runBlocking { mapInParallel(emptyList<Int>(), 0) { it } })
}
