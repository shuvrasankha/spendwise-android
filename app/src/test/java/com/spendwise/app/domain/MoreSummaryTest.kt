package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoreSummaryTest {
    private val money = { n: Double -> "₹${n.toLong()}" }

    @Test fun summarisesEachMoneyRow() {
        val s = moreSummary(56000.0, 1500.0, 500.0, 2000.0, 5320.0, 42500.0, 3, 1, money)
        assertEquals("₹56000 this month", s.income)
        assertEquals("You owe ₹1500 · ₹500 owed to you", s.debts)
        assertEquals("₹2000 put in this month", s.invested)
        assertEquals("₹37180 left of ₹42500", s.budget)
        assertEquals("3 goals, 1 reached", s.goals)
    }

    @Test fun saysSoWhenThereIsNothing() {
        val s = moreSummary(0.0, 0.0, 0.0, 0.0, 0.0, null, 0, 0, money)
        assertEquals("Nothing added this month", s.income)
        assertEquals("All settled", s.debts)
        assertEquals("Nothing put in this month", s.invested)
        assertEquals("No monthly budget set", s.budget)
        assertEquals("No goals yet", s.goals)
        assertEquals("1 goal", moreSummary(0.0, 0.0, 300.0, 0.0, 0.0, null, 1, 0, money).goals)
        assertEquals("₹300 owed to you", moreSummary(0.0, 0.0, 300.0, 0.0, 0.0, null, 1, 0, money).debts)
    }
}
