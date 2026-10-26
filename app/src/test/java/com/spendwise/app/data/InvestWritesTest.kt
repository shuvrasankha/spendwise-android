package com.spendwise.app.data

import com.spendwise.app.domain.EntryEdit
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.HoldingFields
import com.spendwise.app.domain.InvestEntryInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestWritesTest {
    private val ts = "SERVER_TS"
    private val del = "DELETE"
    private val allowed = setOf("uid", "name", "kind", "value", "valueDate", "goalId", "createdAt") // firestore.rules › validHolding
    private val fd = Holding("h2", "SBI FD", "fd", 105000.0, "2026-09-30", "g1")

    @Test fun newHolding_onlyAllowedKeys_inWebOrder() {
        val doc = newHoldingFields("u1", HoldingFields("SBI FD", "fd", 105000.0, "2026-10-24", "g1"), ts)
        assertEquals(listOf("uid", "name", "kind", "value", "valueDate", "goalId", "createdAt"), doc.keys.toList())
        assertEquals(105000L, doc["value"])
        assertEquals(listOf("uid", "name", "kind", "createdAt"), newHoldingFields("u1", HoldingFields("Gold", "gold", null, null, null), ts).keys.toList())
    }

    @Test fun editHolding_clearsWhatWasRemoved() {
        val cleared = editedHoldingFields(HoldingFields("SBI FD", "fd", null, null, null), fd, del)
        assertEquals(mapOf("name" to "SBI FD", "kind" to "fd", "value" to del, "valueDate" to del, "goalId" to del), cleared)
        val kept = editedHoldingFields(HoldingFields("SBI FD", "fd", 99.5, "2026-09-30", "g1"), fd, del)
        assertEquals(mapOf("name" to "SBI FD", "kind" to "fd", "value" to 99.5, "valueDate" to "2026-09-30", "goalId" to "g1"), kept)
        assertTrue(allowed.containsAll(cleared.keys))
        // nothing to clear on a holding that never had them
        assertEquals(setOf("name", "kind"), editedHoldingFields(HoldingFields("A", "mf", null, null, null), fd.copy(value = null, valueDate = null, goalId = null), del).keys)
    }

    @Test fun putMoneyIn_expense() {
        val doc = newInvestEntryFields("u1", InvestEntryInput(5000.0, "2026-10-05", "Net Banking", "", fd, "monthly"), ts)
        assertEquals(
            listOf("uid", "amount", "category", "date", "payment", "cardName", "description", "notes", "tags", "holdingId", "encoding", "repeat", "createdAt"),
            doc.keys.toList(),
        )
        assertEquals(5000L, doc["amount"])
        assertEquals("Savings & Investments", doc["category"])
        assertEquals("SBI FD", doc["description"])
        assertEquals("h2", doc["holdingId"])
        assertEquals(emptyList<String>(), doc["tags"])
        assertEquals("plain", doc["encoding"])
        val once = newInvestEntryFields("u1", InvestEntryInput(1.5, "2026-10-05", "UPI", "n", fd, null), ts)
        assertTrue("repeat" !in once)
        assertEquals(1.5, once["amount"])
    }

    @Test fun editEntry() {
        assertEquals(
            mapOf("amount" to 12L, "date" to "2026-10-01", "holdingId" to "h2", "description" to "SBI FD"),
            editedEntryFields(EntryEdit(12.0, "2026-10-01", fd), hadHoldingId = false, delete = del),
        )
        assertEquals(mapOf("amount" to 12L, "date" to "2026-10-01", "holdingId" to del), editedEntryFields(EntryEdit(12.0, "2026-10-01", null), true, del))
        assertEquals(setOf("amount", "date"), editedEntryFields(EntryEdit(12.0, "2026-10-01", null), false, del).keys)
    }
}
