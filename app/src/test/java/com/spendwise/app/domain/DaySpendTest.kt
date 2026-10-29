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

    @Test fun detail() {
        assertEquals("Nothing added yet", daySpendDetail(0))
        assertEquals("1 expense", daySpendDetail(1))
        assertEquals("3 expenses", daySpendDetail(3))
    }
}
