package com.spendwise.app.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklyBillTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(s: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(s), zone)

    @Test fun weeklyNoteIsNextSundayEvening() {
        assertEquals(Duration.ofHours(55), untilWeeklyNote(at("2026-11-06T12:00"))) // Friday noon → Sunday 19:00
        assertEquals(Duration.ofHours(1), untilWeeklyNote(at("2026-11-08T18:00"))) // Sunday, before
        assertEquals(Duration.ofDays(7), untilWeeklyNote(at("2026-11-08T19:00"))) // Sunday, exactly then: next week
    }

    @Test fun weekQuestionIsAdviceOnThisWeek() {
        val r = routeQuestion(WEEK_QUESTION, "2026-11-08") as AssistantRoute.Advise
        assertEquals("2026-11-02" to "2026-11-08", r.from to r.to)
    }

    @Test fun billMessageCarriesQuestionAndImage() {
        val m = billMessages(currencyOf(null), "What is the late fee?", "data:image/jpeg;base64,AA")
        assertEquals(listOf("system", "user"), m.map { it.first })
        val parts = m[1].second as JsonArray
        assertEquals("What is the late fee?", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("data:image/jpeg;base64,AA", parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertTrue(m[0].second.jsonPrimitive.content.contains(FOLLOW_UPS_TAG))
    }
}
