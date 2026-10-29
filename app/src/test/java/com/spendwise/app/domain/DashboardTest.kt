package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Hand-worked examples for the Dashboard (spec §2.10, §6.2, §9). */
class DashboardTest {

    private fun income(amount: Double, date: String) = Income("$date-$amount", amount, date, "Salary", "", "", "")
    private fun debt(amount: Double, type: String, settled: Boolean = false, paid: List<Payment> = emptyList(), shared: Boolean = false) =
        Debt("$type-$amount", amount, type, "Asha", "2026-10-01", "", settled, "", paid, shared = shared)

    // 10 Oct 2026: day 10 of 31
    private val today = LocalDate.of(2026, 10, 10)
    private val expenses = listOf(
        ex(700.0, "Shopping", "2026-10-15", "Shoes"), // future-dated: not in the month so far
        ex(500.0, FOOD, "2026-10-10", "Lunch"),
        ex(1000.0, FOOD, "2026-10-05"),
        ex(2000.0, INVEST, "2026-10-03", "SIP"),
        ex(3500.0, "Travel", "2026-10-02", "Train"),
        ex(9999.0, FOOD, "2026-09-30"), // last month
    )
    private val home = homeFigures(
        expenses = expenses,
        income = listOf(income(20000.0, "2026-10-01"), income(5000.0, "2026-09-28")),
        debts = listOf(
            debt(1000.0, I_OWE, paid = listOf(Payment(200.0, "2026-10-05"))),
            debt(300.0, THEY_OWE, settled = true),
            debt(400.0, THEY_OWE, shared = true),
        ),
        settings = UserSettings(Budget(8000.0, mapOf("Travel" to 3000.0))),
        holdings = emptyList(),
        today = today,
    )

    @Test fun monthFigures() {
        assertEquals("October", home.monthName)
        assertEquals(5000.0, home.spent, 0.0)
        assertEquals(2000.0, home.invested, 0.0)
        assertEquals(20000.0, home.income, 0.0)
        assertEquals(1, home.incomeCount)
        assertEquals(15000.0, home.saved!!, 0.0)
        assertEquals(800.0, home.owe, 0.0) // 1,000 less 200 paid
        assertEquals(400.0, home.owed, 0.0) // the settled one doesn't count; the shared one does
    }

    @Test fun meterAndPace() {
        val m = home.meter as Meter.Budget
        assertEquals(0.625, m.state.ratio, 1e-12)
        assertEquals(3000.0, m.state.left, 0.0)
        assertEquals(BudgetLevel.OK, m.state.level)
        assertEquals(500.0, home.perDay!!, 0.0)
        // 14,999 flexible since the first entry (30 Sep, 11 days ago), for the 21 days left
        assertEquals(5000.0 + 14999.0 / 11 * 21, home.forecastTotal!!, 1e-6)
    }

    @Test fun meterFallsBackToIncome_thenHides() {
        val noBudget = homeFigures(expenses, listOf(income(20000.0, "2026-10-01")), emptyList(), UserSettings(), emptyList(), today)
        assertEquals(Meter.Income(0.25, 20000.0, 5000.0), noBudget.meter)
        val none = homeFigures(expenses, emptyList(), emptyList(), UserSettings(), emptyList(), today)
        assertNull(none.meter)
        assertNull(none.saved)
        assertNull(none.score)
    }

    @Test fun today() {
        assertEquals(1, home.todays.size)
        assertEquals(500.0, home.todaySpent, 0.0)
        assertEquals(500.0, home.average, 0.0) // (5,000 − 500) / 9 earlier days
        val c = home.todayCompare!!
        assertEquals(0.0, c.gap, 1e-9)
        assertFalse(c.over)
        assertEquals(500.0 / 550, c.fill, 1e-12) // scale = max(500, 500) × 1.1
    }

    @Test fun today_noAverageOnTheFirst() {
        val first = homeFigures(listOf(ex(50.0, FOOD, "2026-10-01")), emptyList(), emptyList(), UserSettings(), emptyList(), LocalDate.of(2026, 10, 1))
        assertEquals(0.0, first.average, 0.0)
        assertNull(first.todayCompare)
    }

    @Test fun score() {
        // saving 40, investing 15, debt 0.96 × 20 = 19.2, budget pace projects 1.94× → 0 of 30: 74.2 / 105
        val s = home.score!!
        assertEquals(71, s.score)
        assertEquals("Good", s.label)
        assertEquals("Spending is running ahead of your budget", s.tip)
        assertEquals(listOf("Saving", "Investing", "Debt", "Budget pace"), s.parts.map { it.name })
        assertEquals(0.96, s.parts[2].value, 1e-12)
    }

    @Test fun whereItWent() {
        assertEquals(listOf("Travel", "Food & Dining"), home.categories.map { it.category })
        val travel = home.categories[0]
        assertEquals(70.0, travel.share, 1e-9)
        assertEquals(BudgetLevel.OVER, travel.cap!!.level)
        assertEquals(1.0, travel.fill, 0.0) // its own limit, capped
        assertEquals(0.3, home.categories[1].fill, 1e-12) // its share
        assertEquals("70", toFixed0(travel.share))
    }

    @Test fun whereItWent_topFivePlusWarnings() {
        val cats = listOf("A", "B", "C", "D", "E", "F", "G")
        val list = cats.mapIndexed { i, c -> ex(100.0 - i, c, "2026-10-02") }
        val f = homeFigures(list, emptyList(), emptyList(), UserSettings(Budget(null, mapOf("G" to 100.0))), emptyList(), today)
        assertEquals(listOf("A", "B", "C", "D", "E", "G"), f.categories.map { it.category }) // G is 94% of its limit
    }

    @Test fun toFixed0_roundsTheExactBinaryValue() {
        assertEquals("3", toFixed0(2.5))
        assertEquals("1", toFixed0(1.005))
        assertEquals("0", toFixed0(0.49999999999999994))
    }

    @Test fun linkedToGoals_valueThenWhatWasPutInAfter() {
        val holdings = listOf(
            Holding("h1", "Index fund", "mf", 10000.0, "2026-10-01", "g1"),
            Holding("h2", "Gold", "other", null, null, "g1"),
        )
        val list = listOf(
            ex(500.0, INVEST, "2026-09-20", holdingId = "h1"), // before the value date: already in it
            ex(700.0, INVEST, "2026-10-05", " index FUND "), // matched by name
            ex(300.0, INVEST, "2026-10-02", holdingId = "h2"),
            ex(900.0, INVEST, "2026-10-30", holdingId = "h2"), // future-dated
        )
        assertEquals(mapOf("g1" to 11000.0), linkedToGoals(portfolio(holdings, list, "2026-10-26").rows))
        val goal = Goal("g1", "Trip", 50000.0, "2027-03", 1000.0)
        assertEquals(12000.0, withLinked(goal, mapOf("g1" to 11000.0)).saved, 0.0)
    }

    // ── Spending over time. Wed 28 Oct 2026; the week started Mon 26 Oct. ──

    private val wed = LocalDate.of(2026, 10, 28)
    private val oct = Period("10", "2026")
    private val a = ex(100.0, FOOD, "2026-10-28", "a")
    private val b = ex(1000.0, INVEST, "2026-10-27", "b")
    private val c = ex(200.0, FOOD, "2026-10-26", "c")
    private val d = ex(50.0, FOOD, "2026-10-25", "d")
    private val f = ex(300.0, FOOD, "2025-03-10", "f")
    private val g = ex(70.0, FOOD, "2026-11-02", "g")
    private val all = listOf(g, a, b, c, d, f)

    private fun rows(tab: TimeTab, period: Period = oct) = tableRows(all, tab, period, wed).map { it.description }

    @Test fun table_perTab_includesInvestments() {
        assertEquals(listOf("a"), rows(TimeTab.DAILY))
        assertEquals(listOf("a", "b", "c"), rows(TimeTab.WEEKLY))
        assertEquals(listOf("a", "b", "c", "d"), rows(TimeTab.MONTHLY))
        assertEquals(listOf("g", "a", "b", "c", "d"), rows(TimeTab.YEARLY)) // month ignored; future-dated counts
    }

    @Test fun table_quirks() {
        assertEquals(emptyList<String>(), rows(TimeTab.DAILY, Period("09", "2026"))) // daily stays inside the picker
        assertEquals(listOf("a", "b", "c", "d"), rows(TimeTab.MONTHLY, Period(null, null))) // falls back to this month
        assertEquals(listOf("f"), rows(TimeTab.YEARLY, Period("10", "2025")))
    }

    @Test fun summary_followsThePickerOnly_andLeavesOutInvestments() {
        val s = periodSummary(all, oct)
        assertEquals(PeriodSummary(350.0, 3, 350.0 / 3, 200.0), s)
        assertEquals(PeriodSummary(420.0, 4, 105.0, 200.0), periodSummary(all, Period(null, "2026")))
        assertEquals(PeriodSummary(0.0, 0, 0.0, 0.0), periodSummary(all, Period("01", "2024")))
    }

    @Test fun trend_dailyIgnoresThePicker() {
        val t = trend(all, TimeTab.DAILY, Period("03", "2025"), wed)
        assertEquals(listOf("Thu 22", "Fri 23", "Sat 24", "Sun 25", "Mon 26", "Tue 27", "Wed 28"), t.labels)
        assertEquals(listOf(0.0, 0.0, 0.0, 50.0, 200.0, 0.0, 100.0), t.values)
    }

    @Test fun trend_weeklyLabelsAcrossMonths() {
        val t = trend(all, TimeTab.WEEKLY, oct, wed)
        assertEquals(listOf("5–11 Oct", "12–18 Oct", "19–25 Oct", "26 Oct–1 Nov"), t.labels)
        assertEquals(listOf(0.0, 0.0, 50.0, 300.0), t.values)
    }

    @Test fun trend_monthlyAndYearly() {
        val y2025 = trend(all, TimeTab.MONTHLY, Period(null, "2025"), wed)
        assertEquals(12, y2025.values.size)
        assertEquals(300.0, y2025.values[2], 0.0)
        val now = trend(all, TimeTab.MONTHLY, Period(null, null), wed)
        assertEquals(350.0, now.values[9], 0.0)
        assertEquals(70.0, now.values[10], 0.0)
        assertEquals(Trend(listOf("2025", "2026"), listOf(300.0, 420.0)), trend(all, TimeTab.YEARLY, oct, wed))
    }

    @Test fun yearOptions_thisYearPlusYearsWithData() {
        assertEquals(listOf("2026", "2025"), yearOptions(all, wed))
        assertEquals(listOf("2027", "2026", "2025"), yearOptions(all, LocalDate.of(2027, 1, 1)))
        assertTrue(yearOptions(emptyList(), wed) == listOf("2026"))
    }

    @Test fun `period as History dates`() {
        assertEquals("2024-02-01" to "2024-02-29", Period("02", "2024").dateRange())
        assertEquals("2026-01-01" to "2026-12-31", Period(null, "2026").dateRange())
        assertNull(Period("10", null).dateRange())
        assertNull(Period(null, null).dateRange())
    }
}
