package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Ported from tests/insights.test.mjs (factsText, renderMarkdown) and tests/ask.test.mjs (readSpec, askPrompt).
class CoachTest {
    private val money = { n: Double -> "Rs ${Math.round(n)}" }
    private val range = (periodRange(InsightPeriod.MONTH, LocalDate.of(2026, 10, 24)) as Checked.Ok).value
    private fun exp(date: String, category: String, amount: Double) =
        Expense("e$date$amount", amount, category, date, "UPI", "", "secret note", "x", emptyList())

    @Test fun `facts for the AI carry numbers only`() {
        val expenses = listOf(exp("2026-10-02", "Food & Dining", 1200.0), exp("2026-10-05", "Shopping", 800.0), exp("2026-10-06", "Savings & Investments", 500.0))
        val income = listOf(Income("i", 10000.0, "2026-10-01", "Acme salary", "Online", "", "secret"))
        val debts = listOf(Debt("d", 5000.0, I_OWE, "Ravi", "2026-10-03", "secret", false, "", emptyList()))
        val text = factsText(summarize(expenses, income, debts, range), range, money)
        assertTrue(text, text.contains("SPENT: Rs 2000 in 2 expenses"))
        assertTrue(text.contains("INVESTED: Rs 500"))
        assertTrue(text.contains("SAVED: Rs 8000 (80.0% of income, including what was invested)"))
        assertTrue(text.contains("user owes Rs 5000"))
        assertFalse(text.contains("secret") || text.contains("Ravi") || text.contains("Acme"))
    }

    @Test fun `no income, no savings line`() {
        val text = factsText(summarize(listOf(exp("2026-10-02", "Food & Dining", 1200.0)), emptyList(), emptyList(), range), range, money)
        assertTrue(text.contains("INCOME: none recorded"))
        assertFalse(text.contains("SAVED:"))
        assertTrue(text.contains("DEBTS NOW: none unsettled"))
    }

    @Test fun `markdown lists, headings, bold and italic`() {
        val blocks = markdownBlocks("Based on your data:\n\n1. Cut **Shopping**\n2. Keep *rent*\n\n### Next\n- <script>alert(1)</script>")
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf("Based on your data:")),
                MdBlock.Items(true, listOf("Cut ${BOLD_ON}Shopping$BOLD_OFF", "Keep ${ITALIC_ON}rent$ITALIC_OFF")),
                MdBlock.Heading("Next"),
                MdBlock.Items(false, listOf("<script>alert(1)</script>")), // plain text on screen, never markup
            ),
            blocks,
        )
        assertEquals(listOf(MdBlock.Paragraph(listOf("No response."))), markdownBlocks(""))
    }

    @Test fun `the AI's reply is checked`() {
        val spec = readSpec("""Sure! {"type":"expense","text":"Amazon","tag":null,"categories":["Shopping","Crypto"],"from":"2026-09-30","to":"2026-09-01","measure":"sum"}""")
        assertEquals(AskSpec(AskType.EXPENSE, "amazon", null, listOf("Shopping"), "2026-09-01", "2026-09-30", AskMeasure.TOTAL), spec)
        assertNull(readSpec("I could not understand that."))
        assertTrue(askPrompt("2026-10-24").contains("Saturday, 2026-10-24"))
    }

    @Test fun `toFixed as JS`() {
        assertEquals("80.0", jsToFixed(80.0, 1))
        assertEquals("13", jsToFixed(12.5, 0))
        assertEquals("-0", jsToFixed(-0.4, 0))
    }
}
