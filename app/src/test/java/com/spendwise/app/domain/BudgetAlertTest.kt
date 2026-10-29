package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetAlertTest {
    private val money = { n: Double -> "₹${n.toLong()}" }
    private val today = "2026-10-29"

    private fun alert(spent: Double, last: String? = null, budget: Double? = 1000.0) =
        budgetAlert(listOf(ex(spent, FOOD, "2026-10-05"), ex(5000.0, INVEST, "2026-10-05"), ex(900.0, FOOD, "2026-09-30")), budget, today, last, money)

    @Test fun `under 80 percent, no budget, or no limit says nothing`() {
        assertNull(alert(799.0))
        assertNull(alert(900.0, budget = null))
        assertNull(alert(900.0, budget = 0.0))
    }

    @Test fun `near, then over, once each a month`() {
        val near = alert(850.0)!!
        assertEquals(BudgetLevel.NEAR, near.level)
        assertEquals("You've used 85% of this month's budget", near.title)
        assertEquals("₹850 spent of ₹1000, ₹150 left.", near.text)
        assertNull(alert(900.0, budgetAlertSent(near, today)))

        val over = alert(1200.0, "2026-10:NEAR")!!
        assertEquals(BudgetLevel.OVER, over.level)
        assertEquals("₹1200 spent of ₹1000, ₹200 over.", over.text)
        assertNull(alert(1300.0, "2026-10:OVER"))
    }

    @Test fun `a new month starts over, and over skips near`() {
        assertEquals(BudgetLevel.NEAR, alert(850.0, "2026-09:OVER")!!.level)
        assertEquals(BudgetLevel.OVER, alert(1200.0)!!.level)
    }
}
