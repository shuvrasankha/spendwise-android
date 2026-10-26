package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The web's own vectors: tests/score.test.mjs and tests/recurring.test.mjs. */
class ScoreAndRecurringTest {

    private fun score(
        income: Double = 1000.0, spent: Double = 800.0, invested: Double = 100.0, budget: Double? = 0.0,
        monthShare: Double = 1.0, owe: Double = 0.0, goals: List<GoalLevel> = emptyList(),
    ) = financeScore(income, spent, invested, budget, monthShare, owe, goals)

    @Test fun score_noIncomeNoScore() = assertNull(score(income = 0.0))

    @Test fun score_perfect() {
        val s = score()!!
        assertEquals(100, s.score)
        assertEquals("Great", s.label)
        assertEquals("Everything is on track this month", s.tip)
        assertEquals(listOf(ScorePart("Saving", 1.0), ScorePart("Investing", 1.0), ScorePart("Debt", 1.0)), s.parts)
    }

    @Test fun score_overspendingAndDebt() {
        val s = score(spent = 1200.0, owe = 500.0)!!
        assertEquals(33, s.score)
        assertEquals("Try to keep 20% of your income unspent", s.tip)
        assertEquals("Needs work", s.label)
    }

    @Test fun score_budgetPace() {
        assertEquals(100, score(budget = 1600.0, spent = 800.0, monthShare = 0.5)!!.score)
        val over = score(budget = 1000.0, spent = 750.0, monthShare = 0.5)!!
        assertEquals(Math.round(75.0 / 105 * 100).toInt(), over.score) // 71
        assertEquals("Spending is running ahead of your budget", over.tip)
    }

    @Test fun score_lateGoals() = assertEquals(94, score(goals = listOf(GoalLevel.LATE, GoalLevel.ON))!!.score)

    @Test fun score_lowInvesting() {
        val s = score(invested = 50.0)!!
        assertEquals(90, s.score)
        assertEquals("Putting 10% of your income into investments will lift your score", s.tip)
    }

    private val rent = ex(18000.0, "Rent", "2026-09-01", "Flat rent", repeat = "monthly", id = "r1")
    private val netflix = ex(649.0, "Subscription", "2026-09-25", "Netflix", repeat = "monthly", id = "n1")

    @Test fun due_onceItsDayHasCome() {
        assertEquals(listOf("r1" to "2026-10-01"), dueRecurring(listOf(rent, netflix), "2026-10-04").map { it.template.id to it.due })
        assertEquals(0, dueRecurring(listOf(rent, rent.copy(id = "r2", date = "2026-10-01")), "2026-10-04").size)
        val manual = ex(18000.0, "Home & Rent", "2026-10-02", "flat rent ")
        assertEquals(0, dueRecurring(listOf(rent, manual), "2026-10-04").size)
        val emi = rent.copy(id = "e1", description = "EMI", date = "2026-10-31")
        assertEquals(0, dueRecurring(listOf(emi), "2026-11-29").size)
        assertEquals("2026-11-30", dueRecurring(listOf(emi), "2026-11-30")[0].due)
        assertEquals(0, dueRecurring(listOf(rent), "2026-10-04") { k, m -> k == recurringKey(rent) && m == "2026-10" }.size)
        assertEquals(1, dueRecurring(listOf(rent), "2026-11-04") { _, m -> m == "2026-10" }.size)
        assertEquals(0, dueRecurring(listOf(rent, rent.copy(id = "r2", date = "2026-09-02", repeat = "stopped")), "2026-10-04").size)
        assertEquals(0, dueRecurring(listOf(rent.copy(repeat = null)), "2026-10-04").size)
    }

    @Test fun upcomingBills_thisMonthThenNext() {
        fun dues(list: List<Expense>, today: String, skipped: SkippedBill = NOTHING_SKIPPED) =
            upcomingBills(list, today, skipped).map { it.title to it.due }
        assertEquals(listOf("Netflix" to "2026-10-25", "Flat rent" to "2026-11-01"), dues(listOf(rent, netflix), "2026-10-04"))
        val paid = netflix.copy(id = "n2", date = "2026-10-20", repeat = null)
        assertEquals(listOf("Netflix" to "2026-11-25"), dues(listOf(netflix, paid), "2026-10-04"))
        assertEquals(listOf("Netflix" to "2026-11-25"), dues(listOf(netflix), "2026-10-04") { _, _ -> true })
        assertEquals(listOf("Netflix" to "2026-12-31"), dues(listOf(netflix.copy(date = "2026-01-31")), "2026-12-31"))
        assertEquals(listOf("Netflix" to "2027-01-31"), dues(listOf(netflix.copy(date = "2026-01-31"), paid.copy(date = "2026-12-02")), "2026-12-31"))
        assertEquals(emptyList<Pair<String, String>>(), dues(listOf(netflix.copy(repeat = "stopped")), "2026-10-04"))
    }

    private fun phone(date: String, total: Int = 6, before: Int? = null) =
        ex(5000.0, "Shopping", date, "iPhone EMI", repeat = "monthly", emiTotal = total, emiPaidBefore = before, id = date)

    @Test fun emi_countsPaymentsAndStops() {
        val paid = listOf("2026-05-10", "2026-06-10", "2026-07-10", "2026-08-10", "2026-09-10").map { phone(it) }
        assertEquals(listOf("2026-10-10" to Emi(5, 6)), dueRecurring(paid, "2026-10-12").map { it.due to it.emi })
        val all = paid + phone("2026-10-10")
        assertEquals(0, dueRecurring(all, "2026-11-12").size)
        assertEquals(0, upcomingBills(all, "2026-10-20").size)
        assertEquals(Emi(5, 12), dueRecurring(listOf(phone("2026-09-10", 12, 4)), "2026-10-12")[0].emi)
        assertEquals(0, dueRecurring(listOf(phone("2026-09-10", 12, 11)), "2026-10-12").size)
    }

    private fun daysBack(i: Int) = LocalDate.of(2026, 10, 24).minusDays(i.toLong()).toString()

    @Test fun forecast_spentBillsPaceAndNextMonth() {
        val list = listOf(
            ex(6500.0, "Home & Rent", "2026-09-01", "Rent", repeat = "monthly"),
            ex(6500.0, "Home & Rent", "2026-10-01", "Rent", repeat = "monthly"),
            ex(600.0, "Bills & Utilities", "2026-09-28", "Wifi", repeat = "monthly"),
            ex(5000.0, "Shopping", "2026-09-26", "Phone EMI", repeat = "monthly", emiTotal = 2),
        ) + (0 until 90).map { ex(100.0, FOOD, daysBack(it), id = "f$it") } +
            ex(5000.0, INVEST, "2026-10-05", "SIP")
        val f = forecast(list.sortedByDescending { it.date }, "2026-10-24")
        assertEquals(6500.0 + 24 * 100, f.spent, 1e-9)
        assertEquals(
            listOf(Triple("Phone EMI", 5000.0, "2026-10-26"), Triple("Wifi", 600.0, "2026-10-28")),
            f.billsDue.map { Triple(it.title, it.amount, it.due) },
        )
        assertEquals(100L, Math.round(f.perDay))
        assertEquals(7, f.daysLeft)
        assertEquals(8900L + 5600 + 700, Math.round(f.total))
        assertEquals(2, f.next.bills)
        assertEquals(7100L + 30 * 100, Math.round(f.next.total))
    }

    @Test fun forecast_skippedBillAndNewAccount() {
        val list = listOf(
            ex(200.0, FOOD, "2026-10-24", "y"),
            ex(300.0, FOOD, "2026-10-20", "x"),
            ex(600.0, "Bills & Utilities", "2026-09-28", "Wifi", repeat = "monthly"),
        )
        val f = forecast(list, "2026-10-24") { k, m -> k.startsWith("Bills") && m == "2026-10" }
        assertEquals(0.0, f.billsTotal, 0.0)
        assertEquals(19L, Math.round(f.perDay))
    }

    @Test fun forecast_oneOffsLeftOut() {
        val days = (0 until 90).map { ex(100.0, FOOD, daysBack(it), id = "f$it") }
        val hotel = ex(10000.0, "Travel", daysBack(40), "Hotel")
        val f = forecast((days + hotel).sortedByDescending { it.date }, "2026-10-24")
        assertEquals(1, f.oneOffs)
        assertEquals(100L, Math.round(f.perDay))
        assertEquals(0, forecast(listOf(days[0], days[1], hotel), "2026-10-24").oneOffs)
    }
}
