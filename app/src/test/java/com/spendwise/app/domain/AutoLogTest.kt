package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AutoLogTest {
    private val sms = "Rs 450.00 debited from A/c XX1234 on 09-11-26 to VPA swiggy@icici. UPI Ref 123456"

    private fun item(id: String) = reviewItem(id, parseSms(sms, LocalDate.of(2026, 11, 9)) as SmsEntry.Spent)

    @Test fun bankSms_becomesAReviewItem() {
        val i = item("a")
        assertEquals(450.0, i.amount, 0.0)
        assertEquals("Food & Dining", i.category)
        assertEquals("UPI", i.payment)
    }

    @Test fun sameMessageTwice_queuedOnce_andTheListIsCapped() {
        val once = queued(emptyList(), item("a"))
        assertEquals(1, queued(once, item("a")).size)
        val full = (1..REVIEW_LIMIT + 5).fold(emptyList<ReviewItem>()) { l, n -> queued(l, item("$n")) }
        assertEquals(REVIEW_LIMIT, full.size)
        assertEquals("6", full.first().id) // the oldest five went
    }

    @Test fun fingerprint_sameTextAndTime_only() {
        assertEquals(smsFingerprint(sms, 1L), smsFingerprint(" $sms ", 1L))
        assertFalse(smsFingerprint(sms, 1L) == smsFingerprint(sms, 2L))
    }

    @Test fun input_keepsTheEditedDescription_orFallsBackWhenCleared() {
        val i = item("a")
        assertEquals("Dinner with Rahul", i.toInput("  Dinner with Rahul ").description)
        assertEquals(i.description, i.toInput("   ").description)
        assertEquals(i.date, i.toInput("x").date)
    }

    @Test fun possiblyAdded_sameAmountSameDay() {
        val i = item("a")
        val e = Expense("1", 450.0, "Food & Dining", i.date, "UPI", "", "", "", emptyList())
        assertTrue(possiblyAdded(i, listOf(e)))
        assertFalse(possiblyAdded(i, listOf(e.copy(date = "2026-11-08"))))
        assertFalse(possiblyAdded(i, listOf(e.copy(amount = 451.0))))
    }

    @Test fun json_roundTrips_andBadTextReadsEmpty() {
        val list = listOf(item("a"), item("b"))
        assertEquals(list, readReview(writeReview(list)))
        assertEquals(emptyList<ReviewItem>(), readReview("not json"))
    }
}
