package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DaySpendTest {
    @Test fun `only that day's spending, not savings`() {
        val list = listOf(
            ex(100.0, FOOD, "2026-10-29"),
            ex(50.5, "Transport", "2026-10-29"),
            ex(500.0, INVEST, "2026-10-29"),
            ex(70.0, FOOD, "2026-10-28"),
        )
        assertEquals(DaySpend(150.5, 2), daySpend(list, "2026-10-29"))
        assertEquals(DaySpend(0.0, 0), daySpend(list, "2026-10-30"))
    }

    @Test fun `this month's spending, against the budget`() {
        val list = listOf(ex(300.0, FOOD, "2026-10-01"), ex(500.0, INVEST, "2026-10-02"), ex(70.0, FOOD, "2026-09-30"), ex(9.0, FOOD, "2026-10-30"))
        assertEquals(300.0, monthSpending(list, "2026-10-29"), 0.0)
        val money = { n: Double -> "₹${n.toLong()}" }
        assertEquals("No monthly budget set", monthSpendDetail(300.0, null, money))
        assertEquals("₹700 left of ₹1000", monthSpendDetail(300.0, 1000.0, money))
        assertEquals("₹200 over the ₹1000 budget", monthSpendDetail(1200.0, 1000.0, money))
    }

    @Test fun `today against the average of the days before`() {
        assertEquals(-605.0, dayVsAverage(35737.0, 606.0, 30)!!, 1.0)
        assertEquals(null, dayVsAverage(606.0, 606.0, 30))
        assertEquals(null, dayVsAverage(606.0, 606.0, 1))
    }

    @Test fun detail() {
        assertEquals("Nothing added yet", daySpendDetail(0))
        assertEquals("1 expense", daySpendDetail(1))
        assertEquals("3 expenses", daySpendDetail(3))
    }
}
