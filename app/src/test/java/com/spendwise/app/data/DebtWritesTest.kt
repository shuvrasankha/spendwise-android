package com.spendwise.app.data

import com.spendwise.app.domain.DebtInput
import com.spendwise.app.domain.Payment
import com.spendwise.app.domain.THEY_OWE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtWritesTest {
    private val ts = "SERVER_TS"
    private val del = "DELETE"
    private val input = DebtInput(1768.0, THEY_OWE, "Bibhu", "2026-10-25", "", "")

    @Test fun newDebt_keysInWebOrder_unshared() {
        val doc = newDebtFields("u1", input, null, ts)
        assertEquals(listOf("uid", "amount", "type", "person", "date", "notes", "settled", "settledDate", "encoding", "createdAt"), doc.keys.toList())
        assertEquals(1768L, doc["amount"]) // whole numbers as a Long, as the JS SDK writes
        assertEquals(false, doc["settled"])
        assertEquals("", doc["settledDate"])
        assertEquals("plain", doc["encoding"])
    }

    @Test fun newDebt_shared() {
        val share = Share.of("bibhu@x.com", "", "Me@Y.com")
        val doc = newDebtFields("u1", input.copy(amount = 99.5, email = "bibhu@x.com"), share, ts)
        assertEquals(listOf("withEmail", "fromName", "fromEmail"), doc.keys.toList().takeLast(3))
        assertEquals(99.5, doc["amount"])
        assertEquals("bibhu@x.com", doc["withEmail"])
        assertEquals("Me@Y.com", doc["fromName"]) // no display name: the email, as typed
        assertEquals("me@y.com", doc["fromEmail"])
        assertEquals("Asha", Share.of("a@b.co", "  Asha", "me@y.com").fromName.trim())
    }

    @Test fun pay_partAndSettle() {
        val paid = listOf(Payment(500.0, "2026-10-20"), Payment(268.5, "2026-10-25"))
        val part = payFields(paid, settles = false, date = "2026-10-25", delete = del)
        assertEquals(setOf("paid"), part.keys)
        assertEquals(listOf(mapOf("amount" to 500L, "date" to "2026-10-20"), mapOf("amount" to 268.5, "date" to "2026-10-25")), part["paid"])
        val off = payFields(paid, settles = true, date = "2026-10-25", delete = del)
        assertEquals(mapOf("paid" to paidField(paid), "settled" to true, "settledDate" to "2026-10-25", "claimDate" to del), off)
    }

    @Test fun claims_touchOnlyClaimFields() {
        assertEquals(mapOf("claimDate" to "2026-10-25", "claimAmount" to 300L), claimFields(300.0, "2026-10-25"))
        assertEquals(mapOf("claimDate" to del, "claimAmount" to del), clearClaimFields(del))
        val confirm = confirmClaimFields(listOf(Payment(300.0, "2026-10-25")), settles = false, date = "2026-10-25", delete = del)
        assertEquals(setOf("paid", "claimDate", "claimAmount"), confirm.keys)
        val settles = confirmClaimFields(emptyList(), settles = true, date = "2026-10-25", delete = del)
        assertEquals("2026-10-25", settles["settledDate"])
        assertEquals(true, settles["settled"])
    }

    @Test fun updates_neverTouchOwnerOrShareFields() {
        val forbidden = setOf("uid", "withEmail", "fromEmail", "fromName")
        listOf(
            payFields(emptyList(), true, "2026-10-01", del), claimFields(1.0, "2026-10-01"), clearClaimFields(del),
            confirmClaimFields(emptyList(), true, "2026-10-01", del), reopenFields(null), reopenFields(emptyList()),
        ).forEach { assertFalse(it.keys.any { k -> k in forbidden }) }
    }

    @Test fun reopen() {
        assertEquals(mapOf("settled" to false, "settledDate" to ""), reopenFields(null))
        assertTrue("paid" in reopenFields(listOf(Payment(1.0, "2026-10-01"))))
    }
}
