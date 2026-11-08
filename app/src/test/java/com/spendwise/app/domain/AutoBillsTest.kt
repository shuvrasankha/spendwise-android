package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBillsTest {
    private val rent = ex(15000.0, "Housing & Rent", "2026-08-05", "Rent", repeat = "monthly", id = "r1")

    @Test fun `first run adds only this month's, once its day has come`() {
        assertTrue(autoBills("u", listOf(rent), "2026-11-04", null).isEmpty())
        val bills = autoBills("u", listOf(rent), "2026-11-05", null)
        assertEquals(listOf("2026-11-05"), bills.map { it.due })
        assertEquals(autoBillId("u", recurringKey(rent), "2026-11"), bills.single().id)
    }

    @Test fun `later runs catch up the missed months, each on its due day`() {
        val bills = autoBills("u", listOf(rent), "2026-11-20", ranThrough = "2026-08")
        assertEquals(listOf("2026-09-05", "2026-10-05", "2026-11-05"), bills.map { it.due })
    }

    @Test fun `already entered this month, skipped, or stopped adds nothing`() {
        assertTrue(autoBills("u", listOf(rent.copy(id = "r2", date = "2026-11-01"), rent), "2026-11-20", "2026-10").isEmpty())
        assertTrue(autoBills("u", listOf(rent), "2026-11-20", "2026-11", { k, m -> k == recurringKey(rent) && m == "2026-11" }).isEmpty())
        assertTrue(autoBills("u", listOf(rent.copy(repeat = "stopped")), "2026-11-20", "2026-08").isEmpty())
    }

    @Test fun `an emi stops at its last payment`() {
        val emi = ex(5000.0, "EMI & Loans", "2026-09-10", "Car", repeat = "monthly", emiTotal = 3, id = "e1")
        val bills = autoBills("u", listOf(emi.copy(id = "e0", date = "2026-08-10"), emi), "2027-01-15", "2026-09")
        assertEquals(listOf("2026-10-10"), bills.map { it.due }) // 2 entered + 1 = 3 of 3
    }

    @Test fun `catch-up is capped, and ids differ by user and month`() {
        val bills = autoBills("u", listOf(rent), "2028-11-20", "2026-08")
        assertEquals(AUTO_BILL_MONTHS, bills.size)
        assertEquals("2027-12-05", bills.first().due)
        assertNotEquals(autoBillId("u", "k", "2026-11"), autoBillId("v", "k", "2026-11"))
        assertNotEquals(autoBillId("u", "k", "2026-11"), autoBillId("u", "k", "2026-12"))
    }

    @Test fun `a month's last day keeps a 31st bill`() {
        val late = ex(649.0, "Entertainment", "2026-10-31", "Netflix", repeat = "monthly", id = "n1")
        assertEquals(listOf("2026-11-30"), autoBills("u", listOf(late), "2026-12-01", "2026-11").map { it.due })
    }
}
