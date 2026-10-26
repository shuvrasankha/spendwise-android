package com.spendwise.app.data.worker

import org.junit.Assert.assertEquals
import org.junit.Test

// The bodies must match the web's JSON.stringify, which the Worker parses.
class WorkerPushTest {
    @Test fun `logged body`() = assertEquals("""{"date":"2026-10-26"}""", loggedBody("2026-10-26").toString())

    @Test fun `debt and claim body`() = assertEquals("""{"debtId":"d1","symbol":"₹"}""", debtPushBody("d1", "₹").toString())

    @Test fun `ok is any 2xx`() {
        assertEquals(true, WorkerResponse(204, "").ok)
        assertEquals(false, WorkerResponse(429, "").ok)
    }
}

class AiRequestTest {
    @Test fun `request body as the web sends it`() = assertEquals(
        """{"model":"m","messages":[{"role":"system","content":"s"},{"role":"user","content":"u"}],"max_tokens":400,"temperature":0.2}""",
        aiRequest("m", listOf("system" to "s", "user" to "u"), 400, 0.2).toString(),
    )

    @Test fun `reply content`() {
        assertEquals("hi", aiContent("""{"choices":[{"message":{"content":"hi"}}]}"""))
        assertEquals(null, aiContent("""{"choices":[]}"""))
        assertEquals(null, aiContent("not json"))
    }
}
