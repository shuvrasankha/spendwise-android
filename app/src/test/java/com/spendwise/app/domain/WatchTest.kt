package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTest {
    private var n = 0
    private fun e(amount: Double, date: String, category: String = "Bills & Utilities", description: String = "", repeat: String? = null) =
        Expense("e${n++}", amount, category, date, "UPI", "", description, "", emptyList(), repeat)
    private val money = { x: Double -> "₹" + jsToFixed(x, 0) }

    @Test fun anExpenseFarAboveItsCategoryIsFlagged() {
        val usual = listOf("2026-08-05", "2026-08-20", "2026-09-05", "2026-09-20", "2026-10-05").map { e(1000.0, it) }
        val big = e(4200.0, "2026-11-03", description = "Electricity")
        val u = unusualExpenses(usual + big, "2026-11-01", "2026-11-06")
        assertEquals(listOf(big.id), u.map { it.expense.id })
        assertEquals("4× your usual ₹1000 on Bills & Utilities", u.single().why(money))
    }

    @Test fun tooLittleHistoryOrARepeatingBillIsNotFlagged() {
        val few = listOf(e(1000.0, "2026-10-01"), e(1000.0, "2026-10-10"))
        assertTrue(unusualExpenses(few + e(9000.0, "2026-11-02"), "2026-11-01", "2026-11-06").isEmpty())
        val usual = (1..5).map { e(1000.0, "2026-10-0$it") }
        assertTrue(unusualExpenses(usual + e(9000.0, "2026-11-02", repeat = "monthly"), "2026-11-01", "2026-11-06").isEmpty())
    }

    @Test fun theSameChargeTwiceOnADayIsADuplicate() {
        val a = e(499.0, "2026-11-04", "Shopping", "Amazon")
        val b = e(499.0, "2026-11-04", "Shopping", "amazon ")
        val u = unusualExpenses(listOf(a, b), "2026-11-01", "2026-11-06")
        assertEquals(1, u.size)
        assertEquals(UnusualKind.DUPLICATE, u.single().kind)
    }

    @Test fun aMonthlyChargeNotMarkedRepeatingIsASubscription() {
        val netflix = listOf("2026-08-03", "2026-09-03", "2026-10-03", "2026-11-03").map { e(649.0, it, description = "Netflix") }
        val gym = listOf("2026-09-01", "2026-10-01", "2026-11-01").map { e(1500.0, it, "Health", "Gym") }
        val marked = listOf(e(399.0, "2026-06-01", description = "Spotify", repeat = "monthly")) +
            listOf("2026-09-01", "2026-10-01", "2026-11-01").map { e(399.0, it, description = "Spotify") }
        val varying = listOf(e(200.0, "2026-09-01", description = "Uber"), e(900.0, "2026-10-01", description = "Uber"), e(300.0, "2026-11-01", description = "Uber"))
        val subs = likelySubscriptions(netflix + gym + marked + varying, "2026-11-06")
        assertEquals(listOf("Gym", "Netflix"), subs.map { it.name })
        assertEquals("2026-11-03", subs[1].latest.date)
    }

    @Test fun savingIsIncomeLessSpendingOverThreeWholeMonths() {
        val inc = listOf("2026-08-01", "2026-09-01", "2026-10-01", "2026-11-01").map { Income("i$it", 30000.0, it, "Salary", "", "", "") }
        val exp = listOf(e(60000.0, "2026-09-10", "Shopping"), e(15000.0, "2026-10-10", "Savings & Investments"), e(5000.0, "2026-11-02", "Shopping"))
        assertEquals(10000.0, averageMonthlySaving(exp, inc, "2026-11-06")!!, 0.001)
        assertNull(averageMonthlySaving(exp, emptyList(), "2026-11-06"))
    }

    @Test fun theGoalPlanSaysWhetherItFits() {
        assertEquals(
            "₹10000 a month until March 2027. You've saved about ₹12000 a month lately, so this fits.",
            goalPlanText(50000.0, 0.0, "2027-03", "2026-11-06", 12000.0, money),
        )
        assertEquals(
            "₹10000 a month until March 2027. You've saved about ₹4000 a month lately: ₹6000 short each month.",
            goalPlanText(50000.0, 0.0, "2027-03", "2026-11-06", 4000.0, money),
        )
        assertTrue(goalPlanText(50000.0, 0.0, "2027-03", "2026-11-06", null, money)!!.endsWith("Add your income to see if you're saving enough."))
        assertNull(goalPlanText(0.0, 0.0, "2027-03", "2026-11-06", null, money))
    }

    @Test fun debtFactsCarryAmountsNotNames() {
        val d = Debt("d1", 5000.0, I_OWE, "Ravi Kumar", "2026-10-07", "for rent", false, "", listOf(Payment(1000.0, "2026-10-20")))
        val text = debtFacts(listOf(d), "2026-11-06", money)
        assertEquals("USER OWES (one person each, no interest recorded): ₹4000 left of ₹5000, 30 days old, ₹1000 paid back so far", text)
        assertFalse("Ravi" in text || "rent" in text)
        assertTrue(routeQuestion(DEBT_PLAN_QUESTION, "2026-11-06").let { it is AssistantRoute.Advise && it.topic == CoachTopic.DEBT })
    }
}
