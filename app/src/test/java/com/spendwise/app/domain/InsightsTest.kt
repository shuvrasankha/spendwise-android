package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Insights without AI (insights-data.js, insights-v2.js), every figure worked by hand. */
class InsightsTest {

    private val money = { n: Double -> "₹${Math.round(n)}" }
    private val today = LocalDate.of(2026, 10, 26) // a Monday
    private val rent = "Home & Rent"

    private fun range(p: InsightPeriod, on: LocalDate = today, a: String = "", b: String = "") =
        (periodRange(p, on, a, b) as Checked.Ok).value

    // ── periodRange ──

    @Test fun month_toTodayAgainstTheSameDaysOfLastMonth() {
        assertEquals(
            PeriodRange("2026-10-01", "2026-10-26", "October 2026 (1–26 Oct)", "2026-09-01", "2026-09-26", "the same days of September", 26, 31),
            range(InsightPeriod.MONTH),
        )
        // The 31st compares with the last day of a shorter month; January with December
        val march = range(InsightPeriod.MONTH, LocalDate.of(2026, 3, 31))
        assertEquals("2026-02-01" to "2026-02-28", march.prevStart to march.prevEnd)
        val jan = range(InsightPeriod.MONTH, LocalDate.of(2026, 1, 15))
        assertEquals(Triple("2025-12-01", "2025-12-15", "the same days of December"), Triple(jan.prevStart, jan.prevEnd, jan.prevLabel))
    }

    @Test fun lastMonth_wholeMonthAgainstTheOneBefore() {
        assertEquals(
            PeriodRange("2025-12-01", "2025-12-31", "December 2025", "2025-11-01", "2025-11-30", "November", 31, null),
            range(InsightPeriod.LAST_MONTH, LocalDate.of(2026, 1, 15)),
        )
    }

    @Test fun quarter_thisMonthAndTheTwoBefore_samLengthBefore() {
        // 1 Aug – 26 Oct is 31 + 30 + 26 = 87 days; the 87 days before end on 31 Jul
        assertEquals(
            PeriodRange("2026-08-01", "2026-10-26", "Last 3 months (1 Aug – 26 Oct)", "2026-05-06", "2026-07-31", "the period before", 87, null),
            range(InsightPeriod.QUARTER),
        )
    }

    @Test fun year_soFarAgainstTheSamePartOfLastYear() {
        assertEquals(
            PeriodRange("2026-01-01", "2026-10-26", "2026 so far", "2025-01-01", "2025-10-26", "the same part of 2025", 299, null),
            range(InsightPeriod.YEAR),
        )
        assertEquals("2027-02-28", range(InsightPeriod.YEAR, LocalDate.of(2028, 2, 29)).prevEnd)
    }

    @Test fun custom_checksThenComparesWithTheDaysBefore() {
        fun err(a: String, b: String) = (periodRange(InsightPeriod.CUSTOM, today, a, b) as Checked.Invalid).message
        assertEquals("Pick both a start and an end date.", err("", "2026-10-10"))
        assertEquals("The start date is after the end date.", err("2026-10-11", "2026-10-10"))
        assertEquals(
            PeriodRange("2026-10-01", "2026-10-10", "1 Oct – 10 Oct 2026", "2026-09-21", "2026-09-30", "the period before", 10, null),
            range(InsightPeriod.CUSTOM, a = "2026-10-01", b = "2026-10-10"),
        )
        // Days still to come don't count towards averages
        assertEquals(7, range(InsightPeriod.CUSTOM, a = "2026-10-20", b = "2026-11-05").elapsedDays)
    }

    // ── summarize ──

    private val lunch = ex(500.0, FOOD, "2026-10-05", "Lunch")
    private val expenses = listOf(
        lunch,
        ex(1500.0, "Shopping", "2026-10-12", "Shoes", payment = "Credit Card"),
        ex(15000.0, rent, "2026-10-01", "Rent", repeat = "monthly", payment = "Net Banking"),
        ex(500.0, FOOD, "2026-10-20", "-", payment = ""),
        ex(2000.0, INVEST, "2026-10-03", "SIP"),
        ex(99.0, FOOD, "2026-10-27", "Tomorrow"), // after today: outside the period
        ex(800.0, FOOD, "2026-09-10"),
        ex(300.0, "Travel", "2026-09-15"),
        ex(15000.0, rent, "2026-09-01", "Rent", repeat = "monthly"),
        ex(999.0, FOOD, "2026-09-28"), // after the 26th: outside the comparison
    )
    private val income = listOf(Income("i1", 50000.0, "2026-10-01", "Salary", "", "", ""), Income("i2", 48000.0, "2026-09-01", "Salary", "", "", ""))
    private fun debt(amount: Double, type: String, settled: Boolean = false, paid: List<Payment> = emptyList()) =
        Debt("$amount$type", amount, type, "A", "2026-09-01", "", settled, "", paid)
    private val debts = listOf(debt(1000.0, I_OWE, paid = listOf(Payment(200.0, "2026-10-01"))), debt(500.0, THEY_OWE), debt(300.0, THEY_OWE, settled = true))

    private val s = summarize(expenses, income, debts, range(InsightPeriod.MONTH))

    @Test fun summary_totals() {
        assertEquals(17500.0, s.spent, 0.0) // 500 + 1500 + 15000 + 500
        assertEquals(50000.0, s.earned, 0.0)
        assertEquals(2000.0, s.invested, 0.0)
        assertEquals(4, s.txCount)
        assertEquals(17500.0 / 26, s.dailyAvg, 1e-9)
        assertEquals(16100.0, s.prevSpent, 0.0) // 800 + 300 + 15000
        assertEquals(1400.0 / 16100 * 100, s.spendChange!!, 1e-9)
        assertEquals(15000.0, s.fixed, 0.0)
        assertEquals(Triple(800.0, 500.0, 2), Triple(s.youOwe, s.owedToYou, s.activeDebts))
        assertTrue(s.hasData)
    }

    @Test fun summary_categoriesMoversBiggest() {
        assertEquals(
            listOf(
                CategoryShare(rent, 15000.0, 15000.0 / 17500 * 100, 15000.0, 0.0),
                CategoryShare("Shopping", 1500.0, 1500.0 / 17500 * 100, 0.0, null),
                CategoryShare(FOOD, 1000.0, 1000.0 / 17500 * 100, 800.0, 25.0),
            ),
            s.categories,
        )
        // Rent didn't move; up to 2 that rose (largest first) then 2 that fell
        assertEquals(listOf("Shopping" to 1500.0, FOOD to 200.0, "Travel" to -300.0), s.movers.map { it.category to it.diff })
        assertEquals(listOf("New · ₹1500", "₹800 → ₹1000", "None this time · was ₹300"), s.movers.map { it.sub(money) })
        assertEquals(listOf("+₹1500", "+₹200", "−₹300"), s.movers.map { it.value(money) })
        // Equal amounts keep the list's order: Lunch before the "-" entry
        assertEquals(listOf("Rent", "Shoes", "Lunch"), s.biggest.map { it.title() })
        assertEquals("Home & Rent · 1 Oct", s.biggest[0].meta())
        assertEquals("5 Oct", BigExpense(FOOD, 1.0, "2026-10-05", "").meta())
    }

    @Test fun summary_weekdaysAndPayments() {
        // Mon 5 and 12 Oct: 2000; Tue 20 Oct: 500; Thu 1 Oct: 15000
        assertEquals(listOf(2000.0, 500.0, 0.0, 15000.0, 0.0, 0.0, 0.0), s.weekdays.map { it.amount })
        val w = weekdayCard(s, money)!!
        assertEquals("Most on Thursdays: ₹15000, 86% of this period's spending.", w.lead)
        assertEquals(listOf(2000.0 / 15000 * 100, 6.0, 0.0, 100.0, 0.0, 0.0, 0.0), w.heights)
        // An empty payment reads as Other; equal amounts keep first-seen order
        assertEquals(listOf("Net Banking", "Credit Card", "UPI", "Other"), s.payments.map { it.name })
        assertEquals(listOf("₹15000 · 86%", "₹1500 · 9%", "₹500 · 3%", "₹500 · 3%"), paymentRows(s, money).map { it.second })
    }

    @Test fun cards_shownOnlyWithEnoughEntries() {
        val two = summarize(expenses.take(2), emptyList(), emptyList(), range(InsightPeriod.MONTH))
        assertNull(weekdayCard(two, money)) // under 3 expenses
        assertEquals(2, paymentRows(two, money).size)
        val one = summarize(expenses.take(1), emptyList(), emptyList(), range(InsightPeriod.MONTH))
        assertTrue(paymentRows(one, money).isEmpty()) // under 2
        assertNull(fixedCard(one, money)) // nothing fixed
        val none = summarize(emptyList(), emptyList(), emptyList(), range(InsightPeriod.MONTH))
        assertFalse(none.hasData)
        assertTrue(summarize(emptyList(), emptyList(), listOf(debt(10.0, I_OWE)), range(InsightPeriod.MONTH)).hasData)
    }

    @Test fun compareCard_figureChangeAndRows() {
        val c = compareCard(s, range(InsightPeriod.MONTH), money)
        assertEquals("Compared with the same days of September", c.title)
        assertEquals(InsightFigure("₹1400", "more spent"), c.figure)
        assertEquals(Change("9% higher than the same days of September", up = true), c.change)
        // Saved: (50000 − 17500) − (48000 − 16100) = 600
        assertEquals(listOf("Income" to "₹2000 more", "Saved" to "₹600 more"), c.rows)
        val nothing = compareCard(summarize(expenses.take(1), emptyList(), emptyList(), range(InsightPeriod.MONTH)), range(InsightPeriod.QUARTER), money)
        assertEquals("Nothing to compare yet: no expenses in the period before.", nothing.none)
    }

    @Test fun compareCard_lessAndSame() {
        val r = range(InsightPeriod.MONTH)
        val less = compareCard(s.copy(spent = 16000.0, spendChange = -100.0 / 16100 * 100, incomeCount = 0, prevIncomeCount = 0), r, money)
        assertEquals(InsightFigure("₹100", "less spent"), less.figure)
        assertEquals("1% lower than the same days of September", less.change!!.text)
        assertTrue(less.rows.isEmpty())
        val same = compareCard(s.copy(spent = 16100.5, spendChange = 0.0), r, money)
        assertEquals(InsightFigure("Same", "spending as before"), same.figure)
        assertNull(same.change)
        assertEquals("the same", moreLess(0.4, money))
    }

    @Test fun fixedCard_shares() {
        val f = fixedCard(s, money)!!
        assertEquals(15000.0 / 17500 * 100, f.share, 1e-9)
        assertEquals("₹15000 fixed" to "Repeating bills and EMIs · 86%", f.fixed)
        assertEquals("₹2500 flexible" to "Everything else, where cutting back is up to you · 14%", f.flexible)
    }

    // ── pieSlices ──

    private fun cat(c: String, amount: Double) = CategoryShare(c, amount, 0.0, 0.0, null)

    @Test fun pie_topFourAndOther() {
        val slices = pieSlices(listOf(cat("A", 600.0), cat("B", 500.0), cat("C", 400.0), cat("D", 300.0), cat("E", 200.0), cat("F", 100.0)))
        assertEquals(listOf("A", "B", "C", "D", "Other"), slices.map { it.label })
        assertEquals(300.0, slices[4].amount, 0.0)
        assertTrue(slices[4].other)
        assertEquals(listOf(600, 500, 400, 300, 300).map { it / 2100.0 * 100 }, slices.map { it.share })
        assertEquals(0.0, slices[0].start, 0.0)
        assertEquals(1100 / 2100.0 * 100, slices[2].start, 1e-9)
    }

    @Test fun pie_fiveOrFewerAllShown_zeroLeftOut() {
        val five = pieSlices(listOf(cat("A", 5.0), cat("B", 4.0), cat("C", 3.0), cat("D", 2.0), cat("E", 1.0), cat("Z", 0.0)))
        assertEquals(listOf("A", "B", "C", "D", "E"), five.map { it.label })
        assertFalse(five.any { it.other })
        assertTrue(pieSlices(listOf(cat("A", 0.0))).isEmpty())
        assertEquals("<1%", PieSlice("x", 1.0, false, 0.5, 0.0).shareText())
        assertEquals("33%", PieSlice("x", 1.0, false, 100.0 / 3, 0.0).shareText())
    }

    // ── budgetReport ──

    @Test fun budget_thisMonthJudgedOnTheForecast() {
        val rep = budgetReport(expenses, Budget(20000.0, mapOf(FOOD to 800.0, "Shopping" to 2000.0)), range(InsightPeriod.MONTH), today.toString(), 21000.0)!!
        val m = rep.months.single()
        assertEquals(listOf(17500.0, 21000.0, 20000.0), listOf(m.spent, m.projected, m.limit))
        assertTrue(m.current && m.over && !m.partial)
        assertEquals(0 to 1, rep.within to rep.judged)
        // Food heads for 1000 / 26 days × 31 = 1192.31 against 800; Shopping 1500 / 26 × 31 = 1788.46 against 2000
        assertEquals(listOf(FOOD, "Shopping"), rep.cats.map { it.category })
        assertEquals(1000.0 / 26 * 31, rep.cats[0].avg, 1e-9)
        assertEquals(InsightFigure("₹1000", "over budget at this pace"), rep.figure(money))
        assertEquals(BudgetMonthRow("Oct", true, null, true, "₹17500 so far · heading for ₹21000 of ₹20000"), rep.monthRows(money).single())
        assertEquals(
            listOf(BudgetCatRow(FOOD, "Heading for ₹1192 of ₹800", "₹392 over", true), BudgetCatRow("Shopping", "Heading for ₹1788 of ₹2000", "Within", false)),
            rep.catRows(money),
        )
    }

    @Test fun budget_fixedSpendingIsNotProjected() {
        // Rent is a bill: it heads for 15000, not 15000 / 26 × 31
        val rep = budgetReport(expenses, Budget(null, mapOf(rent to 16000.0)), range(InsightPeriod.MONTH), today.toString(), 21000.0)!!
        assertEquals(15000.0, rep.cats.single().avg, 0.0)
    }

    @Test fun budget_partMonthsShareTheLimit() {
        // 17 Aug – 30 Sep: August counts 15 of its 31 days
        val list = listOf(ex(16000.0, FOOD, "2026-08-20"), ex(20000.0, FOOD, "2026-09-10"))
        val r = range(InsightPeriod.CUSTOM, a = "2026-08-17", b = "2026-09-30")
        val rep = budgetReport(list, Budget(31000.0), r, today.toString(), 99999.0)!!
        assertEquals(listOf(15000.0, 31000.0), rep.months.map { it.limit })
        assertEquals(listOf(true, false), rep.months.map { it.partial })
        assertEquals(1 to 2, rep.within to rep.judged)
        assertEquals(46000.0, rep.limit!!, 0.0)
        assertEquals(InsightFigure("1 of 2", "months within budget"), rep.figure(money))
        assertEquals(
            listOf(
                BudgetMonthRow("Aug", false, "₹1000 over", true, "₹16000 of ₹15000 (part of the month)"),
                BudgetMonthRow("Sep", false, "₹11000 under", false, "₹20000 of ₹31000"),
            ),
            rep.monthRows(money),
        )
    }

    @Test fun budget_categoryLimitsOnly() {
        val list = listOf(ex(1200.0, FOOD, "2026-08-20"), ex(500.0, FOOD, "2026-09-10"))
        val r = range(InsightPeriod.CUSTOM, a = "2026-08-17", b = "2026-09-30")
        val rep = budgetReport(list, Budget(null, mapOf(FOOD to 1000.0)), r, today.toString(), 0.0)!!
        assertNull(rep.figure(money))
        assertTrue(rep.monthRows(money).isEmpty())
        // August's limit is 1000 × 15/31 = 483.87, so 1200 is over; September's 500 is within
        assertEquals(BudgetCatRow(FOOD, "About ₹850 a month of ₹1000", "Over 1 of 2", true), rep.catRows(money).single())
        assertNull(budgetReport(list, Budget(), r, today.toString(), 0.0))
    }

    // ── Forecast card ──

    private val f = Forecast(
        month = "2026-10", spent = 17500.0, perDay = 120.5, daysLeft = 5,
        billsDue = listOf("A", "B", "C", "D").map { UpcomingBill(it, it, 750.0, "2026-10-28") },
        billsTotal = 3000.0, usualLeft = 602.5, oneOffs = 1, total = 21102.5, lastMonthSpent = 16100.0,
        next = NextMonth("2026-11", 15000.0, 1, 3615.0, 18615.0),
    )

    @Test fun forecastCard_wording() {
        val c = forecastCard(f, money)!!
        assertEquals(InsightFigure("₹21103", "by the end of October"), c.figure)
        assertEquals(Change("₹5003 more than September (₹16100)", up = true), c.vsLastMonth)
        assertEquals(listOf(17500 / 21102.5, 3000 / 21102.5, 602.5 / 21102.5), c.parts)
        assertEquals(
            listOf(
                "₹17500 spent so far" to "Up to today",
                "₹3000 in bills still due" to "A, B, C and 1 more",
                "₹603 at your usual pace" to "₹121 a day for the 5 days left, leaving out bills and 1 one-off big expense",
            ),
            c.legend,
        )
        assertEquals(Triple("November", "About ₹18615", "₹15000 in 1 bill and EMIs, ₹3615 at your usual pace"), Triple(c.nextName, c.nextTotal, c.nextNote))
    }

    @Test fun forecastCard_noBillsOneDayOrNothing() {
        val c = forecastCard(f.copy(billsDue = emptyList(), billsTotal = 0.0, daysLeft = 1, oneOffs = 0, lastMonthSpent = 0.0), money)!!
        assertNull(c.vsLastMonth)
        assertEquals("None left this month", c.legend[1].second)
        assertEquals("₹121 a day for the 1 day left, leaving out bills", c.legend[2].second)
        assertNull(forecastCard(f.copy(spent = 0.0, billsTotal = 0.0, perDay = 0.0), money))
    }
}
