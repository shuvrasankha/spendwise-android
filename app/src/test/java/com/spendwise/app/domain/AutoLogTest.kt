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

    @Test fun learned_appliesToTheNextFromThatPayee_only() {
        val first = item("a")
        val memory = learned(emptyMap(), first, "Groceries", "Swiggy Instamart")
        val next = item("b").withLearned(memory)
        assertEquals("Groceries", next.category)
        assertEquals("Swiggy Instamart", next.description)
        val other = first.copy(id = "c", payee = "Uber", description = "Uber").withLearned(memory)
        assertEquals("Food & Dining", other.category)
    }

    @Test fun learned_skipsGenericPayees_andKeepsTheNewestWithinTheLimit() {
        val generic = item("a").copy(payee = "Bank payment")
        assertEquals(emptyMap<String, Learned>(), learned(emptyMap(), generic, "Groceries", "x"))
        val full = (1..LEARNED_LIMIT + 3).fold(emptyMap<String, Learned>()) { m, n -> learned(m, item("$n").copy(payee = "p$n"), "Other", "") }
        assertEquals(LEARNED_LIMIT, full.size)
        assertFalse("out:p1" in full)
        assertTrue("out:p${LEARNED_LIMIT + 3}" in full)
        // A category no longer in the list isn't applied
        assertEquals("Food & Dining", item("z").withLearned(mapOf("out:swiggy" to Learned("Gone", ""))).category)
    }

    @Test fun moneyIn_queuesAsIncome_andNeedsABankWhenOnline() {
        val credit = parseSms("Rs 5,000.00 credited to A/c XX1234 by NEFT from ACME SALARY. HDFC Bank", LocalDate.of(2026, 11, 9)) as SmsEntry.Earned
        val i = reviewItem("s", credit)
        assertTrue(i.income)
        val ok = i.toIncome("Salary") as IncomeCheck.Ok
        assertEquals(5000.0, ok.input.amount, 0.0)
        assertEquals("Salary", ok.input.source)
        assertTrue(i.copy(bank = "").toIncome("Salary") is IncomeCheck.Invalid)
        // Money in and out are remembered apart
        assertEquals(i.category, i.withLearned(learned(emptyMap(), item("a").copy(payee = i.payee), "Groceries", "x")).category)
    }

    @Test fun oldStoredItems_stillRead() {
        val old = """[{"id":"a","amount":1.0,"date":"2026-11-09","category":"Other","payment":"UPI","cardName":"","description":"Shop"}]"""
        val read = readReview(old).single()
        assertEquals("Shop", read.payee)
        assertFalse(read.income)
    }
}
