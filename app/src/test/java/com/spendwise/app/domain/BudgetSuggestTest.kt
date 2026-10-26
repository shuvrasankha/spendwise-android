package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Ported from tests/budget.test.mjs.
class BudgetSuggestTest {
    private val today = "2026-10-25"
    private fun exp(date: String, category: String, amount: Double, repeat: String? = null) =
        Expense("e$date$category", amount, category, date, "UPI", "", "secret note", "", emptyList(), repeat = repeat)
    private fun inc(date: String, amount: Double) = Income("i$date", amount, date, "Salary", "Online", "", "")
    private val income = listOf(inc("2026-07-01", 60000.0), inc("2026-08-01", 60000.0), inc("2026-09-01", 60000.0), inc("2026-10-01", 99999.0))
    private val expenses = listOf("07", "08", "09").flatMap { m ->
        listOf(
            exp("2026-$m-02", "Home & Rent", 15000.0, "monthly"),
            exp("2026-$m-10", "Food & Dining", 12000.0),
            exp("2026-$m-12", "Shopping", 15000.0),
            exp("2026-$m-15", "Groceries", 8000.0),
            exp("2026-$m-20", "Savings & Investments", 5000.0),
        )
    } + exp("2026-10-03", "Shopping", 50000.0) // this month: not counted
    private val goals = listOf(Goal("g", "Goa", 30000.0, "2027-02", 0.0))
    private val facts get() = budgetFacts(expenses, income, goals, today)!!

    @Test fun `facts are monthly averages of the last three whole months, investments apart`() {
        val f = facts
        assertEquals(3, f.months)
        assertEquals(60000.0, f.income, 0.0)
        assertEquals(50000.0, f.spent, 0.0)
        assertEquals(5000.0, f.invested, 0.0)
        assertEquals(mapOf("Home & Rent" to 15000.0, "Food & Dining" to 12000.0, "Shopping" to 15000.0, "Groceries" to 8000.0), f.cats)
        assertEquals(mapOf("Home & Rent" to 15000.0), f.fixed)
        assertEquals(6000.0, f.goalsMonthly, 0.0)
    }

    @Test fun `what the AI sees is numbers only`() {
        val text = factsLines(facts, "₹")
        assertTrue(text.contains("Income: ₹60000"))
        assertTrue(text.contains("Home & Rent: ₹15000 (of which ₹15000 repeating"))
        assertFalse(text.contains("secret") || text.contains("Goa"))
        assertFalse(budgetPrompt(facts, currencyOf("INR")).toList().any { it.contains("secret") || it.contains("Goa") })
    }

    @Test fun `no income in the last three months, no suggestion`() {
        assertNull(budgetFacts(expenses, listOf(inc("2026-10-01", 5.0)), emptyList(), today))
    }

    @Test fun `the on-device plan keeps a fifth of income, trimming flexible spending, never the bills`() {
        val s = suggestFromFacts(facts)!!
        assertEquals(48000.0, s.total, 0.0)
        assertEquals(12000.0, s.saves, 0.0)
        assertEquals(15000.0, s.cats.getValue("Home & Rent"), 0.0)
        assertTrue(s.cats.getValue("Shopping") < 15000 && s.cats.getValue("Food & Dining") < 12000)
        assertTrue(s.cats.values.sum() <= s.total)
    }

    @Test fun `the AI's answer is checked`() {
        val raw = readReply("""{"total": 90000, "cats": {"Home & Rent": 9000, "Shopping": "8020", "Crypto": 5000, "Health": 3000}, "tip": "Cut  shopping."}""")
        val s = checkReply(raw, facts)!!
        assertEquals(mapOf("Home & Rent" to 15000.0, "Shopping" to 8100.0), s.cats)
        assertEquals(60000.0, s.total, 0.0)
        assertEquals("Cut shopping.", s.tip)
        assertNull(checkReply(readReply("""{"total": "lots"}"""), facts))
    }

    @Test fun `reads JSON from a chatty reply`() {
        assertEquals("40000", readReply("Sure! ```json\n{\"total\": 40000, \"cats\": {}}\n```")!!["total"].toString())
        assertNull(readReply("no idea"))
    }

    @Test fun `the line under the button`() {
        val f = facts
        val (keeps, rest) = suggestionText(suggestFromFacts(f)!!, f, fromAi = false) { "₹ ${it.toLong()}" }
        assertEquals("Keeps ₹ 12000 a month (20%) of your ₹ 60000 average income.", keeps)
        assertEquals("Worked out on your device. Change anything, then save.", rest)
    }
}
