package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertTest {
    private val money = { n: Double -> "₹${n.toLong()}" }
    private val today = "2026-10-29"

    private fun alerts(spent: Double, sent: Set<String> = emptySet(), budget: Budget = Budget(total = 1000.0)) =
        budgetAlerts(listOf(ex(spent, FOOD, "2026-10-05"), ex(5000.0, INVEST, "2026-10-05"), ex(900.0, FOOD, "2026-09-30")), budget, today, sent, money)

    @Test fun `under 80 percent, no budget, or no limit says nothing`() {
        assertTrue(alerts(799.0).isEmpty())
        assertTrue(alerts(900.0, budget = Budget()).isEmpty())
        assertTrue(alerts(900.0, budget = Budget(total = 0.0)).isEmpty())
    }

    @Test fun `near, then over, once each a month`() {
        val near = alerts(850.0).single()
        assertEquals(BudgetLevel.NEAR, near.level)
        assertEquals("You've used 85% of this month's budget", near.title)
        assertEquals("₹850 spent of ₹1000, ₹150 left.", near.text)
        assertTrue(alerts(900.0, setOf(budgetAlertSent(near, today))).isEmpty())

        val over = alerts(1200.0, setOf("2026-10:*:NEAR")).single()
        assertEquals(BudgetLevel.OVER, over.level)
        assertEquals("₹1200 spent of ₹1000, ₹200 over.", over.text)
        assertTrue(alerts(1300.0, setOf("2026-10:*:OVER")).isEmpty())
    }

    @Test fun `a new month starts over, and over skips near`() {
        assertEquals(BudgetLevel.NEAR, alerts(850.0, setOf("2026-09:*:OVER")).single().level)
        assertEquals(BudgetLevel.OVER, alerts(1200.0).single().level)
    }

    @Test fun `each category alerts on its own, after the total`() {
        val both = alerts(850.0, budget = Budget(total = 1000.0, cats = mapOf(FOOD to 500.0, "Shopping" to 100.0)))
        assertEquals(listOf(TOTAL to BudgetLevel.NEAR, FOOD to BudgetLevel.OVER), both.map { it.category to it.level })
        assertEquals("You're over your $FOOD limit", both[1].title)
        val catOnly = alerts(450.0, setOf("2026-10:*:NEAR"), Budget(total = 1000.0, cats = mapOf(FOOD to 500.0))).single()
        assertEquals(FOOD to BudgetLevel.NEAR, catOnly.category to catOnly.level)
        assertTrue(alerts(460.0, setOf(budgetAlertSent(catOnly, today)), Budget(cats = mapOf(FOOD to 500.0))).isEmpty())
    }
}
