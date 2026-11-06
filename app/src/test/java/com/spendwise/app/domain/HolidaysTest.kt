package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidaysTest {
    private fun event(date: String, name: String) =
        "BEGIN:VEVENT\r\nDTSTART;VALUE=DATE:$date\r\nDESCRIPTION:Observance\\nTo hide\r\n  more\r\nSUMMARY:$name\r\nEND:VEVENT\r\n"

    @Test fun `the month's events, by date, each once, with folded lines and escapes`() {
        val ics = "BEGIN:VCALENDAR\r\n" + event("20261111", "Bhai Duj") + event("20261108", "Diwali/Deepavali") +
            event("20251108", "Old Diwali") + event("20261201", "December") + event("20261124", "Guru Nanak\r\n  Jayanti") +
            event("20261124", "Day one\\, two") + event("20271108", "Bhai Duj") + "END:VCALENDAR\r\n"
        assertEquals(
            listOf("8 Nov: Diwali/Deepavali", "11 Nov: Bhai Duj", "24 Nov: Guru Nanak Jayanti", "24 Nov: Day one, two"),
            holidaysIn(ics, "2026-11"),
        )
        assertEquals(emptyList<String>(), holidaysIn("not a calendar", "2026-11"))
    }

    @Test fun `the feed follows the currency's country`() {
        assertTrue(holidayFeedUrl(currencyOf("INR"))!!.contains("/en.indian%23holiday"))
        assertTrue(holidayFeedUrl(currencyOf("GBP"))!!.contains("/en.uk%23holiday"))
        assertNull(holidayFeedUrl(currencyOf("BRL")))
    }
}
