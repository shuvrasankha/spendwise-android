package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ask-data.js, with the cases from tests/ask.test.mjs (guessSpec, runQuery, answerFor, periodText). */
class AskTest {
    private val today = "2026-10-24" // a Saturday
    private val money = { n: Double -> "₹" + String.format(java.util.Locale.US, "%,d", Math.round(n)) }

    private fun e(amount: Double, category: String, description: String, date: String, payment: String = "", notes: String = "", tags: List<String> = emptyList(), cardName: String = "") =
        Expense("$date-$description", amount, category, date, payment, cardName, description, notes, tags)

    private val expenses = listOf(
        e(1299.0, "Shopping", "Amazon - shoes", "2026-09-04", "UPI"),
        e(3021.0, "Shopping", "Phone case", "2026-09-20", "Card", notes = "from amazon"),
        e(499.0, "Shopping", "Amazon Prime", "2026-10-02", "Card"),
        e(420.0, "Food & Dining", "Swiggy", "2026-10-22", "UPI", tags = listOf("goa2026")),
        e(800.0, "Food & Dining", "Dinner", "2026-10-19", "Cash"),
        e(5000.0, "Savings & Investments", "SIP", "2026-10-05"),
    )
    private val income = listOf(
        Income("i1", 85000.0, "2026-10-01", "Salary", "", "", ""),
        Income("i2", 12000.0, "2026-09-15", "Freelance", "", "", "logo job"),
    )

    private fun ask(q: String): Triple<AskSpec, AskResult, AskAnswer> {
        val spec = guessSpec(q, today)
        val result = runQuery(spec, expenses, income)
        return Triple(spec, result, answerFor(spec, result, money, today))
    }

    @Test fun theQuestionAsAsked() {
        val (spec, result, answer) = ask("how much I send in amazon last month")
        assertEquals(listOf("amazon", "2026-09-01", "2026-09-30"), listOf(spec.text, spec.from, spec.to))
        assertEquals(AskMeasure.TOTAL to AskType.EXPENSE, spec.measure to spec.type)
        assertEquals(2, result.count) // the shoes, and the case whose note says amazon; not October's Prime
        assertEquals(AskAnswer("₹4,320", "Spent on Amazon in September · 2 expenses."), answer)
    }

    @Test fun dates() {
        assertEquals("2026-10-01" to today, guessSpec("food this month", today).let { it.from to it.to })
        assertEquals("2026-10-19", guessSpec("spent this week", today).from)
        val aug = guessSpec("biggest expense in august", today)
        assertEquals(listOf("2026-08-01", "2026-08-31"), listOf(aug.from, aug.to))
        assertEquals(AskMeasure.BIGGEST, aug.measure)
        assertEquals("2025-11-01", guessSpec("rent in november", today).from) // the most recent past November
        assertEquals("2025-01-01" to "2025-12-31", guessSpec("total in 2025", today).let { it.from to it.to })
        assertEquals("2026-10-18", guessSpec("uber last 7 days", today).from)
        assertNull(guessSpec("all my shopping", today).from)
        assertEquals("2026-10-12" to "2026-10-18", guessSpec("last week", today).let { it.from to it.to })
    }

    @Test fun categoriesBrandsTagsCountsAndIncome() {
        assertEquals(listOf("Food & Dining"), guessSpec("food last week", today).categories)
        val swiggy = guessSpec("swiggy this month", today)
        assertEquals("swiggy" to emptyList<String>(), swiggy.text to swiggy.categories) // the brand, not all food
        val goa = ask("#goa2026 total")
        assertEquals("goa2026" to 420.0, goa.first.tag to goa.second.total)
        assertEquals("2", ask("how many times did I order food this month").third.headline)
        val salary = ask("how much salary did I get this month")
        assertEquals(Triple(AskType.INCOME, "salary", 85000.0), Triple(salary.first.type, salary.first.text, salary.second.total))
        assertEquals(12000.0, ask("freelance income in september").second.total, 0.0)
    }

    @Test fun spendingLeavesInvestmentsOutUnlessAsked() {
        assertEquals(499.0 + 420 + 800, ask("total spent this month").second.total, 0.0)
        assertEquals(5000.0, ask("how much did I invest this month").second.total, 0.0)
    }

    @Test fun cardAndPaymentInTwoFields() {
        val card = listOf(
            e(961.0, "Bills & Utilities", "LPG", "2026-10-22", "Credit Card", cardName = "Icici"),
            e(2400.0, "Bills & Utilities", "Claude subscription", "2026-10-21", "Credit Card", cardName = "ICICI"),
            e(695.0, "Shopping", "Pujo shopping", "2026-10-04", "Credit Card", cardName = "Axis"),
            e(1700.0, "Groceries", "Amazon", "2026-09-16", "Debit Card", cardName = "Sbi"),
        )
        val spec = guessSpec("how much spend on icici credit card this year", today)
        assertEquals("icici credit card", spec.text)
        val r = runQuery(spec, card, emptyList())
        assertEquals(2 to 3361.0, r.count to r.total)
        assertEquals(3, runQuery(guessSpec("credit card spending this year", today), card, emptyList()).count)
    }

    @Test fun fillerNeverStopsAMatch() {
        val garden = listOf(
            e(600.0, "Shopping", "Gardening", "2026-10-10"),
            e(105.0, "Other", "Garden fertilizer", "2026-10-22"),
            e(200.0, "Shopping", "Flower plants", "2026-09-27", tags = listOf("garden")),
            e(300.0, "Groceries", "Vegetables", "2026-10-21"),
        )
        val spec = guessSpec("spending on garden till now", today)
        assertEquals("garden" to null, spec.text to spec.from)
        assertEquals(905.0, runQuery(spec, garden, emptyList()).total, 0.0)
        val ai = spec.copy(text = "garden now") // the same when the AI leaves a filler word in its text
        assertEquals(905.0, runQuery(ai, garden, emptyList()).total, 0.0)
        assertEquals(listOf("icici", "credit", "card"), searchWords("icici credit cards spend"))
        assertEquals(listOf("glass"), searchWords("glass"))
        assertEquals("Spent on Garden in all your entries · 3 expenses.", answerFor(ai, runQuery(ai, garden, emptyList()), money, today).sentence)
    }

    @Test fun nothingFound() {
        assertEquals(AskAnswer("₹0", "Nothing on Netflix in September."), ask("netflix last month").third)
    }

    @Test fun shortDates() {
        assertEquals("Biggest in September: Phone case, 20 Sep.", ask("biggest expense in september").third.sentence)
        assertEquals("from 3 Dec 2025 to 5 Jan", periodText("2025-12-03", "2026-01-05", today))
    }

    @Test fun periodWording() {
        assertEquals("in September", periodText("2026-09-01", "2026-09-30", today))
        assertEquals("in October so far", periodText("2026-10-01", today, today))
        assertEquals("in November 2025", periodText("2025-11-01", "2025-11-30", today))
        assertEquals("in 2025", periodText("2025-01-01", "2025-12-31", today))
        assertEquals("this year", periodText("2026-01-01", today, today))
        assertEquals("on 24 Oct", periodText(today, today, today))
        assertEquals("in all your entries", periodText(null, null, today))
    }

    @Test fun searchedAndHistoryLink() {
        val spec = guessSpec("amazon last month", today)
        assertEquals("Amazon · 1 Sep – 30 Sep · expenses", searchedText(spec, today))
        assertEquals(AskHistory(false, "amazon", "2026-09-01", "2026-09-30", emptyList()), askHistory(spec))
        assertEquals("#goa2026", askHistory(guessSpec("#goa2026", today))!!.q)
        assertNull(askHistory(spec.copy(type = AskType.BOTH)))
    }
}
