package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

// dashboard.js › syncPush and how sw.js shows a pushed message.
class PushDataTest {
    @Test fun `month totals are spending only, this month and last, rounded`() {
        val list = listOf(
            ex(100.4, FOOD, "2026-10-02"), ex(50.2, FOOD, "2026-10-20"), ex(999.0, INVEST, "2026-10-05"),
            ex(10.5, FOOD, "2026-09-30"), ex(7.0, FOOD, "2026-08-31"),
        )
        assertEquals(mapOf("2026-10" to 151L, "2026-09" to 11L), pushMonthTotals(list, "2026-10-28"))
        assertEquals(listOf("2026-01", "2025-12"), pushMonthTotals(emptyList(), "2026-01-03").keys.toList())
    }

    @Test fun `at most 50 bills`() {
        val many = (1..60).map { ex(10.0, FOOD, "2026-10-05", description = "bill $it", repeat = "monthly") }
        assertEquals(PUSH_MAX_BILLS, pushBills(many, "2026-10-04", NOTHING_SKIPPED).size)
    }

    @Test fun `message url and tag`() {
        assertEquals("debts", pushPage("debt.html"))
        assertEquals("insights", pushPage("insights.html"))
        assertEquals("dashboard", pushPage("index.html"))
        assertEquals("dashboard", pushPage(null))
        assertEquals(true, pushIsDebt("debt-abc"))
        assertEquals(true, pushIsDebt("claim-abc"))
        assertEquals(false, pushIsDebt("bill-abc"))
        assertEquals(false, pushIsDebt(null))
    }

    @Test fun `bell items map to the tags their pushes carry`() {
        val ids = listOf("d1:split", "d1:settled", "d2:claim:2026-10-28:500", "d3:split", "d1:split")
        assertEquals(setOf("debt-d1", "claim-d2", "debt-d3"), pushTagsFor(ids))
        assertEquals(emptySet<String>(), pushTagsFor(emptyList()))
    }

    @Test fun `debt alerts lead with the amount, anything else is untouched`() {
        assertEquals("You owe ₹600" to "a@b.com added a debt with you. Tap to review it in Debts.", prettyPush("a@b.com added a debt with you", "You owe ₹600"))
        assertEquals("Your share is ₹300" to "Ann split a bill with you. Note: Dinner. Tap to review it in Debts.", prettyPush("Ann split a bill with you", "You owe ₹300 · Dinner"))
        assertEquals("₹50 owed to you" to "Ann added a debt with you. Tap to see it in Debts.", prettyPush("Ann added a debt with you", "Ann owes you ₹50"))
        assertEquals("Bill due" to "Rent tomorrow", prettyPush("Bill due", "Rent tomorrow"))
    }
}
