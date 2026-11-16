package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LastDaysTest {
    private fun e(date: String, amount: Double, category: String = "Food & Dining") =
        Expense(date + amount, amount, category, date, "Cash", "", "", "", emptyList())

    @Test fun oneTotalPerDay_oldestFirst_spendingOnly() {
        val list = listOf(e("2026-11-09", 100.0), e("2026-11-09", 50.0), e("2026-11-03", 20.0), e("2026-11-02", 999.0), e("2026-11-08", 70.0, "Savings & Investments"))
        val days = lastDays(list, LocalDate.of(2026, 11, 9))
        assertEquals(LocalDate.of(2026, 11, 3), days.first().first)
        assertEquals(listOf(20.0, 0.0, 0.0, 0.0, 0.0, 0.0, 150.0), days.map { it.second })
    }
}
