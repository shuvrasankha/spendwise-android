package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiExtrasTest {
    @Test fun followUpsComeOffTheAnswer() {
        val (text, next) = splitFollowUps("Rent is fine.\n\n- Keep it under 30%.\n**NEXT:** Can I save more? | What about food? | \"Is rent too high?\"")
        assertEquals("Rent is fine.\n\n- Keep it under 30%.", text)
        assertEquals(listOf("Can I save more?", "What about food?", "Is rent too high?"), next)
    }

    @Test fun aReplyWithoutFollowUpsIsAllAnswer() {
        assertEquals("Just this." to emptyList<String>(), splitFollowUps("Just this.\n"))
    }

    @Test fun descriptionsNameTheirCategory() {
        assertEquals("Food & Dining", categoryFromText("Swiggy order"))
        assertEquals("Groceries", categoryFromText("JioMart veggies"))
        assertNull(categoryFromText("gift for mom"))
    }

    @Test fun onlyAKnownCategoryIsTakenFromTheAi() {
        assertEquals("Health", readCategory(" health.\n"))
        assertNull(readCategory("Other"))
        assertNull(readCategory("I think it's food"))
        assertEquals("Food & Dining", readCategory("Category: **Food & Dining**"))
        assertNull(readCategory("Shopping or Groceries")) // two names: unsure
        assertNull(readCategory("Category: Other"))
    }

    @Test fun explainThisMonthIsAdviceOnThisMonth() {
        val r = routeQuestion(EXPLAIN_MONTH_QUESTION, "2026-11-06") as AssistantRoute.Advise
        assertEquals("2026-11-01", r.from)
        assertTrue(coachSystemPrompt(CoachTopic.OVERVIEW, "", currencyOf(null)).let { "Focus" !in it && FOLLOW_UPS_TAG in it })
    }

    @Test fun followUpsGoBackIntoTheHistory() {
        assertEquals("A\n\nNEXT: b | c", withFollowUps("A", listOf("b", "c")))
        val m = remindFollowUps(listOf("user" to "q1", "assistant" to "a", "user" to "q2"))
        assertEquals("q1", m[0].second)
        assertTrue(m[2].second.startsWith("q2") && FOLLOW_UPS_TAG in m[2].second)
    }
}
