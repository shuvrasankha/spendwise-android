package com.spendwise.app.data

import com.spendwise.app.data.expenses.dueExpenseFields
import com.spendwise.app.data.expenses.editedExpenseFields
import com.spendwise.app.data.expenses.newExpenseFields
import com.spendwise.app.domain.Budget
import com.spendwise.app.domain.ExpenseInput
import com.spendwise.app.domain.Goal
import com.spendwise.app.domain.ex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Slice 9 doc shapes: settings (validSettings), repeating and EMI expenses, and the due bill's copy. */
class PlansWritesTest {

    private val createdAt = Any()

    @Test fun budget_onlyTheBudgetKey_numbersLikeTheJsSdk() {
        assertEquals(mapOf("budget" to mapOf("total" to null, "cats" to emptyMap<String, Any>())), budgetFields(Budget()))
        assertEquals(
            mapOf("budget" to mapOf("total" to 20000L, "cats" to mapOf("Food & Dining" to 5000.5))),
            budgetFields(Budget(20000.0, mapOf("Food & Dining" to 5000.5))),
        )
    }

    @Test fun goals_theWebsFiveKeys() {
        val doc = goalsFields(listOf(Goal("lx2a", "Goa trip", 30000.0, "2027-03", 1250.5)))
        assertEquals(
            mapOf("goals" to listOf(mapOf("id" to "lx2a", "name" to "Goa trip", "target" to 30000L, "by" to "2027-03", "saved" to 1250.5))),
            doc,
        )
    }

    private val input = ExpenseInput(4250.0, "Bills & Utilities", "2026-10-05", "UPI", "", "Phone EMI", "", emptyList())

    @Test fun newExpense_repeatAndEmi() {
        val doc = newExpenseFields("u1", input.copy(repeat = "monthly", emiTotal = 6, emiPaidBefore = 2), createdAt)
        assertEquals("monthly", doc["repeat"])
        assertEquals(6, doc["emiTotal"])
        assertEquals(2, doc["emiPaidBefore"])
        val plain = newExpenseFields("u1", input, createdAt)
        listOf("repeat", "emiTotal", "emiPaidBefore").forEach { assertFalse(it, it in plain) }
    }

    @Test fun editedExpense_repeatOnlyWhenSet_neverEmi() {
        assertFalse("repeat" in editedExpenseFields(input))
        val stopped = editedExpenseFields(input.copy(repeat = "stopped"))
        assertEquals("stopped", stopped["repeat"])
        assertFalse("emiTotal" in stopped)
    }

    @Test fun dueBill_copiesTheTemplate() {
        val t = ex(5000.0, "Savings & Investments", "2026-09-05", "Nifty SIP", repeat = "monthly", holdingId = "h1", emiTotal = 12, payment = "")
        val doc = dueExpenseFields("u1", t, "2026-10-05", createdAt)
        assertEquals(
            mapOf(
                "uid" to "u1", "amount" to 5000L, "category" to "Savings & Investments", "date" to "2026-10-05", "payment" to "UPI",
                "cardName" to "", "description" to "Nifty SIP", "notes" to "", "tags" to emptyList<String>(), "repeat" to "monthly",
                "encoding" to "plain", "holdingId" to "h1", "emiTotal" to 12, "createdAt" to createdAt,
            ),
            doc,
        )
        val rent = dueExpenseFields("u1", ex(15000.0, "Home & Rent", "2026-09-01", "", repeat = "monthly"), "2026-10-01", createdAt)
        assertEquals("-", rent["description"])
        listOf("holdingId", "emiTotal", "emiPaidBefore").forEach { assertFalse(it, it in rent) }
    }
}
