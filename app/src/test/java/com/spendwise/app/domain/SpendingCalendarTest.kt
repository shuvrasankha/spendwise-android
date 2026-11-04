package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingCalendarTest {
    @Test fun sumsPerDayAndBucketsAgainstTheMean() {
        val m = spendingCalendar(
            listOf(
                ex(100.0, FOOD, "2026-10-02"), ex(200.0, FOOD, "2026-10-02"),
                ex(30.0, FOOD, "2026-10-05"),
                ex(999.0, INVEST, "2026-10-06"), ex(50.0, FOOD, "2026-09-30"), ex(-5.0, FOOD, "2026-10-07"),
            ),
            "2026-10",
        )
        assertEquals(330.0, m.total, 0.0)
        assertEquals(31, m.days.size)
        assertEquals(3, m.lead) // 1 Oct 2026 is a Thursday
        assertEquals(listOf(0, 2, 1, 0), listOf(m.days[0], m.days[1], m.days[4], m.days[5]).map { it.level })
        assertEquals(300.0, m.days[1].total, 0.0)
    }
}
