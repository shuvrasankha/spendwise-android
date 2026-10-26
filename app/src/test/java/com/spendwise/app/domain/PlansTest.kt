package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Slice 9: repeat and EMI checks, EMI plans, due bills, split, goals and budget, worked by hand. */
class PlansTest {

    private val whole = { n: Double -> "₹${Math.round(n)}" }
    private val bills = "Bills & Utilities"

    private fun check(repeat: Boolean = true, total: String = "", before: String = "", amount: String = "100", category: String = FOOD) =
        checkExpense(amount, category, "2026-10-05", "UPI", "", "Phone", "", emptyList(), repeat, total, before)

    private fun invalid(c: ExpenseCheck) = (c as ExpenseCheck.Invalid).message
    private fun ok(c: ExpenseCheck) = (c as ExpenseCheck.Ok).input

    // ── Repeat and EMI checks (dashboard.js › addExpense) ──

    @Test fun emi_totalMustBeWhole2to600() {
        listOf("1", "601", "2.5", "abc").forEach {
            assertEquals(it, "Total payments should be a whole number from 2 to 600.", invalid(check(total = it)))
        }
        assertEquals(600, ok(check(total = "600")).emiTotal)
    }

    @Test fun emi_alreadyPaid() {
        assertEquals("Already paid should be a whole number below 6, counting this payment out.", invalid(check(total = "6", before = "6")))
        assertEquals("Already paid needs Total payments too.", invalid(check(before = "-1")))
        assertEquals("Add Total payments for this EMI.", invalid(check(before = "3")))
        assertNull(ok(check(before = "0")).emiTotal)
    }

    @Test fun emi_checkedInTheWebsOrder() {
        assertEquals("Enter a valid amount.", invalid(check(amount = "", total = "1")))
        assertEquals("Total payments should be a whole number from 2 to 600.", invalid(check(total = "1", category = "")))
    }

    @Test fun emi_okAndRepeatOff() {
        val i = ok(check(total = "6", before = "2"))
        assertEquals(Triple("monthly", 6, 2), Triple(i.repeat, i.emiTotal, i.emiPaidBefore))
        assertNull(ok(check(total = "6", before = "0")).emiPaidBefore)
        // Off: the EMI fields aren't read at all
        val off = ok(check(repeat = false, total = "x", before = "y"))
        assertEquals(listOf(null, null, null), listOf(off.repeat, off.emiTotal, off.emiPaidBefore))
    }

    @Test fun editedRepeat_ticksStopsOrKeeps() {
        assertEquals("monthly", editedRepeat(true, null))
        assertEquals("stopped", editedRepeat(false, "monthly"))
        assertEquals("stopped", editedRepeat(false, "stopped"))
        assertNull(editedRepeat(false, null))
    }

    // ── Expense page figures (§6.4) ──

    @Test fun expenseFigures_monthYearAllAndDailyAverage() {
        val f = expenseFigures(
            listOf(
                ex(30.0, FOOD, "2026-10-20"), // future: in the year (§9.3), not the month
                ex(50.0, FOOD, "2026-10-10"),
                ex(1000.0, INVEST, "2026-10-02"),
                ex(100.0, FOOD, "2026-10-01"),
                ex(200.0, FOOD, "2026-03-01"),
                ex(400.0, FOOD, "2025-12-31"),
            ),
            LocalDate.of(2026, 10, 10),
        )
        assertEquals(ExpenseFigures(150.0, 2, 380.0, 4, 780.0, 5, 15.0), f)
    }

    // ── EMI plans (helpers.js › emiPlans) ──

    private val today = LocalDate.of(2026, 10, 26)

    @Test fun emiPlans_scheduleAfterThisMonthsPayment() {
        // 6 payments, 2 before the app, entered Sep and Oct: 4 paid, Nov and Dec to go
        val list = listOf(
            ex(4250.0, bills, "2026-10-05", "Phone EMI", repeat = "monthly", emiTotal = 6, emiPaidBefore = 2),
            ex(4250.0, bills, "2026-09-05", "Phone EMI", repeat = "monthly", emiTotal = 6, emiPaidBefore = 2),
        )
        val p = emiPlans(list, today.toString()).single()
        assertEquals(listOf(4, 6), listOf(p.paid, p.total))
        assertEquals(8500.0, p.left, 0.0)
        assertEquals("2026-11-05", p.next)
        assertEquals("2026-12", p.ends)
        assertEquals("4 of 6 paid · ₹8500 left · Next 5 Nov · Ends Dec 2026", p.meta(today, whole))
        assertEquals(67, p.percent)
    }

    @Test fun emiPlans_notYetPaidThisMonthIsDue() {
        val p = emiPlans(listOf(ex(3000.0, bills, "2026-09-03", "Laptop", repeat = "monthly", emiTotal = 5)), today.toString()).single()
        assertEquals("2026-10-03", p.next)
        assertEquals("2027-01", p.ends) // 4 to go: Oct, Nov, Dec, Jan
        assertEquals("1 of 5 paid · ₹12000 left · Due 3 Oct · Ends Jan 2027", p.meta(today, whole))
    }

    @Test fun emiPlans_31stFallsOnFebruarysLastDay() {
        val p = emiPlans(listOf(ex(999.0, bills, "2027-01-31", "TV", repeat = "monthly", emiTotal = 12)), "2027-02-10").single()
        assertEquals("2027-02-28", p.next)
    }

    @Test fun emiPlans_paidOffShownStoppedRunningHidden_runningFirst() {
        val list = listOf(
            ex(500.0, bills, "2026-10-01", "Earbuds", repeat = "monthly", emiTotal = 3),
            ex(800.0, bills, "2026-08-15", "Watch", repeat = "stopped", emiTotal = 4), // stopped before the end
            ex(700.0, bills, "2026-07-15", "Fridge", repeat = "stopped", emiTotal = 3, emiPaidBefore = 2),
        )
        val plans = emiPlans(list, today.toString())
        assertEquals(listOf("Earbuds", "Fridge"), plans.map { it.title })
        assertEquals("3 of 3 paid · Paid off Jul 2026", plans[1].meta(today, whole))
        assertEquals("₹500 a month", emiHeader(plans, whole))
        assertEquals("All paid off", emiHeader(plans.drop(1), whole))
        assertEquals(33, plans[0].percent)
    }

    @Test fun emiPercent_neverBelowTwo() = assertEquals(2, EmiPlan("k", ex(1.0, bills, "2026-10-01"), 1, 100, 1.0, 99.0, false, "x", "2026-11-01", "2035-01").percent)

    // ── Due this month ──

    @Test fun due_metaAndSkip() {
        val rent = ex(15000.0, "Home & Rent", "2026-09-05", "Rent", repeat = "monthly")
        val phone = ex(4250.0, bills, "2026-09-07", "Phone EMI", repeat = "monthly", emiTotal = 6, emiPaidBefore = 3)
        val due = dueRecurring(listOf(phone, rent), today.toString())
        assertEquals(listOf("Rent", "Phone EMI"), due.map { billTitle(it.template) })
        assertEquals("EMI 5 of 6 · Bills & Utilities · due 7 Oct", due[1].meta(today))
        val skipped = homeFigures(listOf(phone, rent), emptyList(), emptyList(), UserSettings(), emptyList(), today) { key, month ->
            "$key:$month" == "${recurringKey(rent)}:2026-10"
        }
        assertEquals(listOf("Phone EMI"), skipped.due.map { billTitle(it.template) })
    }

    // ── Split (split.js, helpers.js › splitAmount) ──

    @Test fun splitAmount_yourShareTakesTheRounding() {
        assertEquals(Split(33.33, 33.34), splitAmount(100.0, 2))
        assertEquals(Split(250.0, 250.0), splitAmount(1000.0, 3))
        assertEquals(Split(0.03, 0.04), splitAmount(0.1, 2))
        assertEquals(Split(14.29, 14.26), splitAmount(100.0, 6))
    }

    @Test fun checkSplit_rowsAndErrors() {
        val me = "Me@Mail.com"
        assertNull(checkSplit(listOf(SplitRow(), SplitRow(" ", " ")), me))
        assertEquals("Enter a name for each person you split with.", (checkSplit(listOf(SplitRow(email = "a@b.co")), me) as Checked.Invalid).message)
        assertEquals("\"nope\" doesn't look like an email address.", (checkSplit(listOf(SplitRow("A", "nope")), me) as Checked.Invalid).message)
        assertEquals("That's your own email. Use the other person's.", (checkSplit(listOf(SplitRow("A", " ME@mail.com ")), me) as Checked.Invalid).message)
        val ok = checkSplit(listOf(SplitRow(" Riya ", " Riya@Mail.COM "), SplitRow(), SplitRow("Sam")), me) as Checked.Ok
        assertEquals(listOf(SplitPerson("Riya", "riya@mail.com"), SplitPerson("Sam", "")), ok.value)
    }

    @Test fun splitDebts_theyOweEachOnTheDate() {
        val d = splitDebts(listOf(SplitPerson("Riya", "riya@mail.com")), 33.33, "2026-10-05", "Dinner").single()
        assertEquals(DebtInput(33.33, THEY_OWE, "Riya", "2026-10-05", "Split: Dinner", "riya@mail.com"), d)
    }

    @Test fun splitSummary_andSavedText() {
        val money = { n: Double -> "₹" + "%.2f".format(n) }
        assertEquals("" to "Add the people who shared this. It is split equally.", splitSummary(listOf(SplitRow(email = "a@b.co")), "100", money))
        assertEquals("1 person" to "Enter the full amount above to see each share.", splitSummary(listOf(SplitRow("A")), "", money))
        assertEquals(
            "2 people" to "Your share ₹33.34 is saved as the expense · each person owes you ₹33.33",
            splitSummary(listOf(SplitRow("A"), SplitRow("B")), "100", money),
        )
        assertEquals("Your share added. 1 person owes you ₹50.00, see Debts.", splitSaved(1, "₹50.00"))
        assertEquals("Your share added. 2 people owe you ₹33.33 each, see Debts.", splitSaved(2, "₹33.33"))
    }

    // ── Goals (goals.js) ──

    private val g1 = Goal("g1", "Goa trip", 12000.0, "2026-12", 3000.0)

    @Test fun moneyNum_likeTheWeb() {
        assertEquals(20000.0, moneyNum("₹ 20,000")!!, 0.0)
        assertEquals(12.35, moneyNum("12.346")!!, 0.0)
        assertNull(moneyNum("0"))
        assertNull(moneyNum("abc"))
        assertEquals(5.0, moneyNum("-5")!!, 0.0) // the minus is stripped, as on the web
    }

    @Test fun addToGoal_roundsAndCelebratesOnce() {
        val near = g1.copy(saved = 11900.0)
        val done = (addToGoal(listOf(near), near, "100") as Checked.Ok).value
        assertEquals(12000.0, done.goals.single().saved, 0.0)
        assertEquals("g1", done.completed)
        val again = (addToGoal(done.goals, done.goals.single(), "1") as Checked.Ok).value
        assertNull(again.completed)
        assertEquals("Enter how much you put aside.", (addToGoal(listOf(g1), g1, "") as Checked.Invalid).message)
    }

    @Test fun saveGoal_newEditAndErrors() {
        val new = (saveGoal(listOf(g1), null, "  Phone ", "50,000", "2027-03", "", "abc") as Checked.Ok).value
        assertEquals(listOf(g1, Goal("abc", "Phone", 50000.0, "2027-03", 0.0)), new.goals)
        assertNull(new.completed)
        val edited = (saveGoal(listOf(g1), g1, "Goa", "12000", "2026-12", "12000", "zzz") as Checked.Ok).value
        assertEquals(listOf(Goal("g1", "Goa", 12000.0, "2026-12", 12000.0)), edited.goals)
        assertEquals("g1", edited.completed)
        fun err(name: String = "A", target: String = "1", by: String = "2027-01", saved: String = "") =
            (saveGoal(emptyList(), null, name, target, by, saved, "id") as Checked.Invalid).message
        assertEquals("Give the goal a name.", err(name = " ", target = ""))
        assertEquals("Enter the amount as a number.", err(target = "0"))
        assertEquals("Pick the month you want to reach it by.", err(by = ""))
        assertEquals("Enter what you have saved as a number, or leave it empty.", err(saved = "x"))
    }

    @Test fun newGoalId_isBase36() {
        assertEquals("10", newGoalId(36))
        assertEquals("z", newGoalId(35))
    }

    @Test fun goalRows_paceWordingAndLinked() {
        val today = "2026-10-26"
        val on = goalRows(listOf(g1), mapOf("g1" to 1000.0), today).single()
        assertEquals(4000.0, on.goal.saved, 0.0)
        assertEquals(1000.0, on.linked, 0.0)
        assertEquals("₹4000 of ₹12000", on.amounts(whole))
        assertEquals("₹2667 a month · by Dec 2026", on.meta(whole)) // 8000 over Oct, Nov, Dec
        assertEquals(33, on.percent)
        val late = goalRows(listOf(g1.copy(by = "2026-09")), emptyMap(), today).single()
        assertEquals("₹9000 to go · Sep 2026 has passed", late.meta(whole))
        val done = goalRows(listOf(g1.copy(saved = 13000.0)), emptyMap(), today).single()
        assertEquals("Reached. Well done!", done.meta(whole))
        assertEquals(100, done.percent)
    }

    @Test fun goalsNeed_header() {
        val rows = goalRows(listOf(g1), emptyMap(), "2026-10-26") // 9000 over 3 months
        assertEquals("₹3000 a month", goalsNeed(rows, null, whole))
        assertEquals("₹3000 a month · covered", goalsNeed(rows, 3000.0, whole))
        assertEquals("₹3000 a month · ₹0 left this month", goalsNeed(rows, -200.0, whole))
        assertEquals("₹3000 a month · ₹1200 left this month", goalsNeed(rows, 1200.0, whole))
        assertNull(goalsNeed(emptyList(), 100.0, whole))
    }

    // ── Budget (budget.js › save) ──

    @Test fun checkBudget_keepsOnlyReadableLimits() {
        val b = (checkBudget("", mapOf(FOOD to "5000", "Shopping" to "", "Travel" to "abc")) as Checked.Ok).value
        assertEquals(Budget(null, mapOf(FOOD to 5000.0)), b)
        assertEquals(20000.0, (checkBudget("₹20,000", emptyMap()) as Checked.Ok).value.total!!, 0.0)
        assertEquals("Enter the monthly limit as a number, or leave it empty.", (checkBudget("x", emptyMap()) as Checked.Invalid).message)
    }
}
