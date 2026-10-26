package com.spendwise.app.data.expenses

import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.ExpenseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Doc shapes from spec §3.1 and the real export (§3.9)
class ExpenseMapperTest {

    private val createdAt = Any() // stands in for FieldValue.serverTimestamp()

    private val input = ExpenseInput(
        amount = 4250.0, category = "Bills & Utilities", date = "2026-10-05", payment = "Credit Card",
        cardName = "HDFC Millennia", description = "Phone EMI", notes = "", tags = listOf("iphone"),
    )

    @Test fun write_newDocHasExactlyTheWebsFields() {
        val doc = newExpenseFields("u1", input, createdAt)
        assertEquals(
            setOf("uid", "amount", "category", "date", "payment", "cardName", "description", "notes", "tags", "encoding", "createdAt"),
            doc.keys,
        )
        assertEquals("u1", doc["uid"])
        assertEquals(4250L, doc["amount"]) // whole number → int64, like the JS SDK
        assertEquals("plain", doc["encoding"])
        assertEquals(listOf("iphone"), doc["tags"])
        assertTrue(doc["createdAt"] === createdAt)
    }

    @Test fun write_fractionIsADoubleAndEmptyTagsAreStillWritten() {
        val doc = newExpenseFields("u1", input.copy(amount = 182.5, tags = emptyList()), createdAt)
        assertEquals(182.5, doc["amount"])
        assertEquals(emptyList<String>(), doc["tags"])
    }

    @Test fun write_editTouchesOnlyFormFields() {
        val doc = editedExpenseFields(input)
        assertEquals(setOf("amount", "category", "date", "payment", "cardName", "description", "notes", "tags", "encoding"), doc.keys)
        // never written by an edit, so they stay as they were
        listOf("uid", "createdAt", "repeat", "emiTotal", "emiPaidBefore", "holdingId").forEach { assertFalse(it, it in doc) }
    }

    @Test fun read_currentDoc() {
        val doc = mapOf(
            "uid" to "u1", "amount" to 4250L, "category" to "Bills & Utilities", "date" to "2026-10-05",
            "payment" to "Credit Card", "cardName" to "HDFC Millennia", "description" to "Phone EMI", "notes" to "",
            "tags" to listOf("iphone"), "encoding" to "plain", "createdAt" to Any(),
            "repeat" to "monthly", "emiTotal" to 12L, "emiPaidBefore" to 3L, "holdingId" to "h7",
        )
        assertEquals(
            Expense(
                id = "x1", amount = 4250.0, category = "Bills & Utilities", date = "2026-10-05", payment = "Credit Card",
                cardName = "HDFC Millennia", description = "Phone EMI", notes = "", tags = listOf("iphone"),
                repeat = "monthly", emiTotal = 12, emiPaidBefore = 3, holdingId = "h7",
            ),
            expenseFrom("x1", doc),
        )
    }

    @Test fun read_legacyStringAmountDocWithoutEncodingTagsOrCardName() {
        // The one doc from 2026-03-14: amount is a numeric string, and there's no encoding, tags or cardName
        val doc = mapOf(
            "uid" to "u1", "amount" to "250", "category" to "Food & Dining", "date" to "2026-03-14",
            "payment" to "UPI", "description" to "Lunch at office", "notes" to "", "createdAt" to Any(),
        )
        val e = expenseFrom("old", doc)
        assertEquals(250.0, e.amount, 0.0)
        assertEquals(emptyList<String>(), e.tags)
        assertEquals("", e.cardName)
        assertNull(e.repeat)
    }

    @Test fun read_oldCategoryNamesShowUnderTheCurrentOnes() {
        assertEquals("Savings & Investments", expenseFrom("a", mapOf("category" to "Investment")).category)
        assertEquals("Bills & Utilities", expenseFrom("a", mapOf("category" to "Subscription")).category)
        assertEquals("Other", expenseFrom("a", emptyMap()).category)
    }

    @Test fun read_nearlyEmptyDocNeverCrashes() {
        val e = expenseFrom("a", mapOf("uid" to "u1", "amount" to "YUVV", "tags" to listOf("ok", 5, null)))
        assertEquals(0.0, e.amount, 0.0) // legacy XOR text reads as 0, as on the web
        assertEquals(listOf("ok"), e.tags)
        assertEquals("", e.date)
        assertEquals("", e.description)
    }

    @Test fun read_pendingFlagPassesThrough() {
        assertTrue(expenseFrom("a", emptyMap(), pending = true).pending)
    }

    @Test fun roundTrip_writtenDocReadsBackAsTheSameInput() {
        listOf(input, input.copy(amount = 99.99, payment = "UPI", cardName = "", tags = emptyList())).forEach { sent ->
            val e = expenseFrom("id", newExpenseFields("u1", sent, createdAt))
            assertEquals(sent, ExpenseInput(e.amount, e.category, e.date, e.payment, e.cardName, e.description, e.notes, e.tags))
        }
    }
}
