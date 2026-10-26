package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

// Ported from tests/split.test.mjs (notifications, mergeIds), plus the bell's wording.
class NotificationsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun endOfDay(d: String) = LocalDateTime.parse("${d}T23:59:59").atZone(zone).toInstant().toEpochMilli()
    private fun debt(id: String, amount: Double, type: String, person: String, date: String, notes: String = "", settled: Boolean = false, settledDate: String = "", claimDate: String = "", claimAmount: Double? = null, paid: List<Payment> = emptyList(), createdAt: Long = 0, shared: Boolean = false) =
        Debt(id, amount, type, person, date, notes, settled, settledDate, paid, claimDate, claimAmount, shared = shared, createdAt = createdAt)
    private val money = { a: Double -> "₹${jsNumberText(a)}" }

    @Test fun `each split, then its settling, newest first`() {
        val open = debt("a", 300.0, I_OWE, "Ravi", "2026-10-20", "Split: Dinner", createdAt = 1000, shared = true)
        val paid = debt("b", 120.0, I_OWE, "Asha", "2026-10-18", settled = true, settledDate = "2026-10-22", createdAt = 500, shared = true)
        val items = notificationsFrom(listOf(open, paid), emptyList(), zone)
        assertEquals(listOf("b:settled", "a:split", "b:split"), items.map { it.id })
        assertEquals(NotifItem("b:settled", NotifKind.SETTLED, I_OWE, endOfDay("2026-10-22"), "2026-10-22", "Asha", 120.0, note = ""), items[0])
        assertEquals("Ravi", items[1].person)
    }

    @Test fun `a claim on your own debt, until you settle it`() {
        val claimed = debt("c", 500.0, THEY_OWE, "Ravi", "2026-10-10", "Rent", claimDate = "2026-10-23")
        val items = notificationsFrom(emptyList(), listOf(claimed, claimed.copy(id = "d", claimDate = ""), claimed.copy(id = "e", settled = true)), zone)
        assertEquals(listOf(NotifItem("c:claim:2026-10-23:500", NotifKind.CLAIM, THEY_OWE, endOfDay("2026-10-23"), "2026-10-23", "Ravi", 500.0, true, "Rent")), items)
    }

    @Test fun `a part claim carries its amount`() {
        val d = debt("d", 1768.0, THEY_OWE, "Bibhu", "2026-10-01", claimDate = "2026-10-25", claimAmount = 300.0, paid = listOf(Payment(500.0, "2026-10-20")))
        val item = notificationsFrom(emptyList(), listOf(d), zone).single()
        assertEquals(300.0, item.amount, 0.0)
        assertEquals(false, item.settles)
        assertEquals("d:claim:2026-10-25:300", item.id)
    }

    @Test fun `no createdAt falls back to the date at UTC midnight`() {
        val d = debt("x", 10.0, I_OWE, "R", "2026-10-20", shared = true)
        assertEquals(java.time.Instant.parse("2026-10-20T00:00:00Z").toEpochMilli(), notificationsFrom(listOf(d), emptyList(), zone).single().at)
    }

    @Test fun `seen and cleared lists merge, keeping the newest 200`() {
        assertEquals(listOf("a", "b", "c"), mergeIds(listOf("a", "b"), listOf("b", "c")))
        val many = List(250) { "x$it" }
        assertEquals(200, mergeIds(many, emptyList()).size)
        assertEquals("x249", mergeIds(many, emptyList()).last())
    }

    @Test fun `badge and wording`() {
        assertEquals("3", notifBadge(3))
        assertEquals("9+", notifBadge(12))
        val split = NotifItem("a:split", NotifKind.SPLIT, I_OWE, 0, "2026-10-20", "Ravi", 300.0, note = "Split: Dinner")
        assertEquals("Ravi split a bill with you", notifTitle(split, money))
        assertEquals("You owe ₹300 · Split: Dinner", notifDetail(split, money))
        val debtAdded = split.copy(type = THEY_OWE, note = "")
        assertEquals("Ravi added a debt with you", notifTitle(debtAdded, money))
        assertEquals("Ravi owes you ₹300", notifDetail(debtAdded, money))
        val claim = NotifItem("c", NotifKind.CLAIM, THEY_OWE, 0, "2026-10-23", "Ravi", 500.0, true, "Rent")
        assertEquals("Ravi says ₹500 was paid, all of it", notifTitle(claim, money))
        assertEquals("Confirm it on Debts · Rent", notifDetail(claim, money))
        assertEquals("Asha marked ₹12.5 as paid", notifTitle(split.copy(kind = NotifKind.SETTLED, person = "Asha", amount = 12.5), money))
    }
}
