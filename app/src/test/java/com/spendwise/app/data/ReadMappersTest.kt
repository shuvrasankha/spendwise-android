package com.spendwise.app.data

import com.spendwise.app.domain.Budget
import com.spendwise.app.domain.Goal
import com.spendwise.app.domain.I_OWE
import com.spendwise.app.domain.IncomeInput
import com.spendwise.app.domain.Payment
import com.spendwise.app.domain.THEY_OWE
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.jsNumberOrZero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadMappersTest {

    @Test fun income_tolerant() {
        val i = incomeFrom("i1", mapOf("amount" to "250.5", "date" to "2026-10-01", "source" to "Salary"))
        assertEquals(250.5, i.amount, 0.0)
        assertEquals("", i.bank)
        assertEquals(0.0, incomeFrom("i2", mapOf("amount" to "abc")).amount, 0.0)
        assertEquals(1200.0, incomeFrom("i3", mapOf("amount" to 1200L)).amount, 0.0)
    }

    @Test fun ownDebt_legacyValues() {
        val d = ownDebtFrom(
            "d1",
            mapOf(
                "amount" to 1000L, "type" to I_OWE, "person" to "Asha", "settled" to "true",
                "paid" to listOf(mapOf("amount" to "100", "date" to "2026-10-02"), mapOf("amount" to "x")),
                "claimAmount" to 0L, "withEmail" to "asha@example.com",
            ),
        )
        assertTrue(d.settled)
        assertEquals(listOf(Payment(100.0, "2026-10-02"), Payment(0.0, "")), d.paid)
        assertNull(d.claimAmount)
        assertEquals("asha@example.com", d.email)
        assertFalse(d.shared)
        assertTrue(ownDebtFrom("d2", mapOf("settled" to 1L)).settled)
        assertFalse(ownDebtFrom("d3", mapOf("settled" to "yes")).settled)
    }

    @Test fun sharedDebt_fromTheViewersSide() {
        val d = sharedDebtFrom("d1", mapOf("amount" to "500", "type" to THEY_OWE, "settled" to "true", "claimAmount" to 300L))
        assertEquals(I_OWE, d.type) // they recorded "they owe me", so the viewer owes
        assertEquals("Someone", d.person)
        assertEquals(500.0, d.amount, 0.0)
        assertFalse(d.settled) // only a real true counts here
        assertEquals(300.0, d.claimAmount!!, 0.0)
        assertTrue(d.shared)
        assertEquals(THEY_OWE, sharedDebtFrom("d2", mapOf("type" to I_OWE, "fromName" to "Ravi")).type)
    }

    @Test fun holding_valuedOnlyWithADate() {
        val noDate = holdingFrom("h1", mapOf("name" to "Gold", "value" to 500L))
        assertEquals(500.0, noDate.value!!, 0.0) // kept as stored, for the dialog
        assertFalse(noDate.valued)
        assertFalse(holdingFrom("h2", mapOf("value" to -1L, "valueDate" to "2026-10-01")).valued)
        assertFalse(holdingFrom("h4", mapOf("value" to "500", "valueDate" to "2026-10-01")).valued)
        val h = holdingFrom("h3", mapOf("name" to "Fund", "kind" to "mf", "value" to 0L, "valueDate" to "2026-10-01", "goalId" to "g1"))
        assertEquals(0.0, h.value!!, 0.0)
        assertTrue(h.valued)
        assertEquals("g1", h.goalId)
    }

    @Test fun settings_normalized() {
        assertEquals(UserSettings(), settingsFrom(null))
        val s = settingsFrom(
            mapOf(
                "budget" to mapOf("total" to 0L, "cats" to mapOf("Travel" to 3000L, "Food & Dining" to "x")),
                "goals" to listOf(
                    mapOf("id" to "g1", "name" to "Trip", "target" to 50000L, "by" to "2027-03", "saved" to -5L),
                    mapOf("id" to "g2", "name" to "Bad date", "target" to 100L, "by" to "2027-3"),
                    mapOf("id" to "g3", "name" to "No target", "target" to 0L, "by" to "2027-03"),
                    "junk",
                ),
            ),
        )
        assertEquals(Budget(null, mapOf("Travel" to 3000.0)), s.budget)
        assertEquals(listOf(Goal("g1", "Trip", 50000.0, "2027-03", 0.0)), s.goals)
    }

    @Test fun jsNumberOrZero_likeNumber() {
        assertEquals(5.0, jsNumberOrZero(5L), 0.0)
        assertEquals(12.5, jsNumberOrZero(" 12.5 "), 0.0)
        assertEquals(0.0, jsNumberOrZero(""), 0.0)
        assertEquals(0.0, jsNumberOrZero("12abc"), 0.0)
        assertEquals(1.0, jsNumberOrZero(true), 0.0)
        assertEquals(0.0, jsNumberOrZero(null), 0.0)
    }

    @Test fun incomeEdit_writesTheWebsFieldsOnly() {
        val fields = editedIncomeFields(IncomeInput(85000.0, "2026-10-01", "Salary", "Cash", "", ""))
        assertEquals(
            mapOf("amount" to 85000L, "date" to "2026-10-01", "source" to "Salary", "paymentType" to "Cash", "bank" to "", "notes" to "", "encoding" to "plain"),
            fields,
        )
        assertEquals(1250.5, editedIncomeFields(IncomeInput(1250.5, "2026-10-01", "Bonus", "Online", "SBI", "")).getValue("amount"))
        assertTrue(incomeFrom("i", mapOf("amount" to 1L), pending = true).pending)
    }

    @Test fun incomeAdd_wholeDoc_inTheWebsKeyOrder() {
        val sentinel = Any()
        val doc = newIncomeFields("u1", IncomeInput(85000.0, "2026-10-01", "Salary", "Online", "ICICI", ""), sentinel)
        assertEquals(listOf("uid", "amount", "date", "source", "paymentType", "bank", "notes", "encoding", "createdAt"), doc.keys.toList())
        assertEquals(85000L, doc["amount"])
        assertEquals("plain", doc["encoding"])
        assertTrue(doc["createdAt"] === sentinel)
        assertEquals("u1", doc["uid"])
    }

    @Test fun income_csvImportWithoutEncoding_reads() {
        // A CSV-imported doc (spec §9.6): no encoding, and the amount as the import wrote it
        val i = incomeFrom("c1", mapOf("uid" to "u1", "amount" to 4500L, "date" to "2026-03-14", "source" to "Refund from Amazon", "paymentType" to "Online", "bank" to "", "notes" to ""))
        assertEquals(4500.0, i.amount, 0.0)
        assertEquals("Refund from Amazon", i.source)
        assertFalse(i.pending)
        // And it round-trips: what Android reads is what an edit writes back, now with encoding
        val back = editedIncomeFields(IncomeInput(i.amount, i.date, i.source, i.paymentType, "SBI", i.notes))
        assertEquals("plain", back["encoding"])
        assertEquals(4500L, back["amount"])
    }
}
