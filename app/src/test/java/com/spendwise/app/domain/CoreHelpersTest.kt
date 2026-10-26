package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Vectors from the web's tests (categories, recurring, split, dates .test.mjs), so both clients agree.
class CoreHelpersTest {

    @Test fun normalizeCategory_mapsAliasesKeepsUnknownsAndDefaultsToOther() {
        assertEquals("Bills & Utilities", normalizeCategory("Subscription"))
        assertEquals("Shopping", normalizeCategory("Personal Care"))
        assertEquals("Other", normalizeCategory("Gifts"))
        assertEquals("Home & Rent", normalizeCategory("Rent"))
        assertEquals("Savings & Investments", normalizeCategory("Investment"))
        assertEquals("Groceries", normalizeCategory("Groceries"))
        assertEquals("Pets", normalizeCategory("Pets"))
        assertEquals("Groceries", normalizeCategory("  Groceries "))
        assertEquals("Other", normalizeCategory(""))
        assertEquals("Other", normalizeCategory(null))
    }

    @Test fun categories_everyAliasTargetIsACurrentCategory() {
        assertEquals(12, EXPENSE_CATEGORIES.size)
        assertTrue(CATEGORY_ALIASES.values.all { it in EXPENSE_CATEGORIES })
    }

    @Test fun isSpending_leavesOutSavingsIncludingTheOldName() {
        assertFalse(isSpending("Savings & Investments"))
        assertFalse(isSpending("Investment"))
        assertTrue(isSpending("Food & Dining"))
        assertTrue(isSpending(null))
    }

    @Test fun round2_roundsHalvesUpLikeJs() {
        assertEquals(1.01, round2(1.005 + 1e-9), 0.0)
        assertEquals(2.5, round2(2.5), 0.0)
        assertEquals(-2.0, round2(-2.004), 0.0)
        assertEquals(0.13, round2(0.125), 0.0) // 12.5 → 13, not banker's 12
    }

    @Test fun budgetState_nullWithoutLimitThenOkNearOver() {
        assertNull(budgetState(500.0, 0.0))
        assertNull(budgetState(500.0, null))
        assertNull(budgetState(500.0, Double.NaN))
        assertEquals(BudgetState(0.7, 3000.0, BudgetLevel.OK), budgetState(7000.0, 10000.0))
        assertEquals(BudgetLevel.NEAR, budgetState(8000.0, 10000.0)!!.level)
        assertEquals(BudgetLevel.NEAR, budgetState(10000.0, 10000.0)!!.level)
        assertEquals(BudgetLevel.OVER, budgetState(10500.0, 10000.0)!!.level)
    }

    @Test fun goalPace_countsTheCurrentMonthThenDoneOrLate() {
        val goa = Goal("g", "Goa", target = 30000.0, by = "2027-03", saved = 6000.0)
        assertEquals(GoalPace(0.2, 24000.0, 6, 4000.0, GoalLevel.ON), goalPace(goa, "2026-10-23"))
        assertEquals(24000.0, goalPace(goa, "2027-03-31").monthly, 0.0)
        assertEquals(GoalPace(0.2, 24000.0, 0, 0.0, GoalLevel.LATE), goalPace(goa, "2027-04-01"))
        val done = goalPace(goa.copy(saved = 31000.0), "2026-10-23")
        assertEquals(GoalLevel.DONE, done.level)
        assertEquals(0.0, done.left, 0.0)
        assertEquals(0.0, goalPace(goa.copy(target = 0.0), "2026-10-23").ratio, 0.0)
    }

    @Test fun splitAmount_evenAndYourShareAbsorbsRounding() {
        assertEquals(Split(300.0, 300.0), splitAmount(900.0, 2))
        val split = splitAmount(100.0, 2)
        assertEquals(33.33, split.each, 0.0)
        assertEquals(33.34, split.mine, 0.0)
        assertEquals(100.0, round2(split.each * 2 + split.mine), 0.0)
    }

    private val paid = listOf(Payment(500.0, "2026-10-20"))

    @Test fun debtPaidAndLeft() {
        assertEquals(500.0, debtPaid(paid), 0.0)
        assertEquals(1268.0, debtLeft(1768.0, paid), 0.0)
        assertEquals(1768.0, debtLeft(1768.0, emptyList()), 0.0)
        assertEquals(0.0, debtLeft(100.0, listOf(Payment(150.0, "2026-10-20"))), 0.0)
    }

    @Test fun payDebt_partThenRestTooMuchZeroAndOrder() {
        val part = payDebt(1768.0, paid, 268.0, "2026-10-25") as PayResult.Ok
        assertFalse(part.settles)
        assertEquals(listOf(500.0, 268.0), part.paid.map { it.amount })
        val rest = payDebt(1768.0, part.paid, 1000.0, "2026-10-26") as PayResult.Ok
        assertTrue(rest.settles)
        assertEquals(PayResult.TooMuch(1268.0), payDebt(1768.0, paid, 1300.0, "2026-10-25"))
        assertEquals(PayResult.NotPositive, payDebt(1768.0, paid, 0.0, "2026-10-25"))
        assertEquals(PayResult.NotPositive, payDebt(1768.0, paid, Double.NaN, "2026-10-25"))
        val early = payDebt(1768.0, paid, 10.0, "2026-10-01") as PayResult.Ok
        assertEquals(listOf("2026-10-01", "2026-10-20"), early.paid.map { it.date })
        // within a tenth of a paisa of what's left still counts, and pays it off
        assertTrue((payDebt(100.0, emptyList(), 100.0005, "2026-10-25") as PayResult.Ok).settles)
    }

    @Test fun debtClaim_partAllThatsLeftCappedAndSettled() {
        assertEquals(Claim(300.0, "2026-10-25", false), debtClaim(1768.0, paid, false, "2026-10-25", 300.0))
        assertEquals(Claim(1268.0, "2026-10-25", true), debtClaim(1768.0, paid, false, "2026-10-25", null))
        assertEquals(1268.0, debtClaim(1768.0, paid, false, "2026-10-25", 5000.0)!!.amount, 0.0)
        assertNull(debtClaim(1768.0, paid, true, "2026-10-25", 300.0))
        assertNull(debtClaim(1768.0, paid, false, "", 300.0))
        assertNull(debtClaim(1768.0, paid, false, null, 300.0))
    }

    @Test fun todayStr_isTheLocalCalendarDate() {
        assertEquals("2026-10-01", todayStr(LocalDate.of(2026, 10, 1)))
        assertEquals(todayStr(LocalDate.now()), todayStr())
    }

    @Test fun addMonths_crossesYearsBothWays() {
        assertEquals("2026-11", addMonths("2026-10", 1))
        assertEquals("2027-01", addMonths("2026-12", 1))
        assertEquals("2025-12", addMonths("2026-01", -1))
        assertEquals("2026-10", addMonths("2026-10", 0))
        assertEquals("2028-01", addMonths("2026-10", 15))
    }

    @Test fun weekStart_isMondaySundayGoesBack() {
        assertEquals("2026-10-26", weekStart(LocalDate.of(2026, 10, 26))) // Monday
        assertEquals("2026-10-26", weekStart(LocalDate.of(2026, 10, 29))) // Thursday
        assertEquals("2026-10-26", weekStart(LocalDate.of(2026, 11, 1))) // Sunday
        assertEquals("2026-09-28", weekStart(LocalDate.of(2026, 10, 1))) // across a month
    }

    @Test fun currencyOf_findsCodeAndFallsBackToInr() {
        assertEquals(35, CURRENCIES.size)
        assertEquals(35, CURRENCIES.map { it.code }.toSet().size)
        assertEquals("$", currencyOf("USD").symbol)
        assertEquals("INR", currencyOf("XXX").code)
        assertEquals("INR", currencyOf(null).code)
    }
}
