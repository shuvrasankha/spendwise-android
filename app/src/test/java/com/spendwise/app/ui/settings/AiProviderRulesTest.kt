package com.spendwise.app.ui.settings

import com.spendwise.app.data.ai.AiError
import com.spendwise.app.data.ai.byokErrorText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRulesTest {
    @Test fun `activation needs a saved key and a passed test`() {
        assertTrue(canActivate(hasKey = true, test = KeyTest.Passed))
        assertFalse(canActivate(hasKey = false, test = KeyTest.Passed))
        for (t in listOf(KeyTest.Idle, KeyTest.Running, KeyTest.Failed("x"))) assertFalse(canActivate(hasKey = true, test = t))
    }

    @Test fun `changing a model after a test needs a new test`() {
        val passed = ByokDraft(loaded = true, textModel = "a", visionModel = "b", test = KeyTest.Passed)
        assertEquals(KeyTest.Idle, passed.withModels(text = "a2").test)
        assertEquals(KeyTest.Idle, passed.withModels(vision = "b2").test)
        assertEquals(KeyTest.Passed, passed.withModels(text = "a").test) // the same text isn't a change
    }

    @Test fun `every error has words, none from the provider`() {
        val all = listOf(
            AiError.Unauthorized, AiError.RateLimited, AiError.ModelNotFound, AiError.Busy, AiError.Network, AiError.Timeout,
            AiError.BadReply, AiError.Blocked, AiError.NoConsent, AiError.NoVisionModel, AiError.KeyUnreadable, AiError.Http(500),
        )
        assertEquals(all.size, all.map(::byokErrorText).filter { it.isNotBlank() }.toSet().size)
        assertEquals("API key rejected. Check the key.", byokErrorText(AiError.Unauthorized))
    }
}
