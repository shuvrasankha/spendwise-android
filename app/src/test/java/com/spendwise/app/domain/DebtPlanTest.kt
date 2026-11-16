package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebtPlanTest {
    private fun debt(amount: Double, vararg paid: Payment) =
        Debt("d", amount, I_OWE, "Mom", "2026-11-01", "", false, "", paid.toList())

    private val monthly = DebtPlan(2000.0, PLAN_MONTH, "2026-12-01")

    @Test fun `even plan from the future start`() {
        val p = payoff(debt(10000.0), monthly, "2026-11-08")
        assertEquals(listOf("2026-12-01", "2027-01-01", "2027-02-01", "2027-03-01", "2027-04-01"), p.schedule.map { it.first })
        assertEquals("2027-04-01", p.ends)
        assertEquals(0.0, p.behind, 0.0)
    }

    @Test fun `a payment pulls the end forward, and the last one is the rest`() {
        val p = payoff(debt(10000.0, Payment(4500.0, "2026-12-01")), monthly, "2026-12-02")
        assertEquals(listOf("2027-01-01" to 2000.0, "2027-02-01" to 2000.0, "2027-03-01" to 1500.0), p.schedule)
        assertEquals(0.0, p.behind, 0.0)
    }

    @Test fun `a missed instalment is behind and stays in what's left`() {
        val p = payoff(debt(10000.0), monthly, "2026-12-02")
        assertEquals(2000.0, p.behind, 0.0)
        assertEquals("2027-01-01", p.schedule.first().first)
        assertEquals("2027-05-01", p.ends)
    }

    @Test fun `payments before the start are not counted as on plan`() {
        val p = payoff(debt(10000.0, Payment(9000.0, "2026-11-02")), monthly, "2027-01-02")
        assertEquals(1000.0, p.behind, 0.0) // only 1000 was owed when the plan began
    }

    @Test fun `paid on the due day moves to the next instalment`() {
        val p = payoff(debt(10000.0, Payment(2000.0, "2026-12-01")), monthly, "2026-12-01")
        assertEquals("2027-01-01", p.schedule.first().first)
        assertEquals("2027-04-01", p.ends)
    }

    @Test fun `weekly, and the 31st on a short month`() {
        assertEquals("2026-12-15", planDate(DebtPlan(1.0, PLAN_WEEK, "2026-12-01"), 2))
        assertEquals("2027-02-28", planDate(DebtPlan(1.0, PLAN_MONTH, "2027-01-31"), 1))
    }

    @Test fun `paid off has no schedule, and the sheet's checks`() {
        assertNull(payoff(debt(100.0, Payment(100.0, "2026-12-01")), monthly, "2026-12-02").ends)
        assertEquals("Enter how much each payment is.", planError(debt(100.0), null, "2026-12-01"))
        assertEquals("That's more than what's left.", planError(debt(100.0), 150.0, "2026-12-01"))
        assertNull(planError(debt(100.0), 50.0, "2026-12-01"))
    }
}
