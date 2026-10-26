package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// tests/invest.test.mjs, plus invest.js's wording and checks
class InvestTest {
    private val axis = Holding("h1", "Axis Bluechip", "mf", null, null, "g1")
    private val fd = Holding("h2", "SBI FD", "fd", 105000.0, "2026-09-30", null)
    private val holdings = listOf(axis, fd)
    private val today = "2026-10-24"
    private val whole = { n: Double -> "₹ " + toFixed0(n) }

    private fun sip(date: String, repeat: String = "monthly") = ex(5000.0, INVEST, date, "Axis Bluechip", repeat = repeat, holdingId = "h1")
    private val expenses = listOf(
        sip("2026-08-05"), sip("2026-09-05"), sip("2026-10-05"),
        ex(100000.0, INVEST, "2026-01-10", "SBI FD", holdingId = "h2"),
        ex(2000.0, INVEST, "2026-10-12", "sbi fd "), // made on the expense form: matched by name
        ex(3000.0, INVEST, "2026-10-02", "Gold coin"), // in no holding
        ex(900.0, FOOD, "2026-10-03", "Axis Bluechip"), // spending, never a holding's
        ex(5000.0, INVEST, "2026-11-05", "Axis Bluechip", holdingId = "h1"), // future: not counted yet
    )

    @Test fun holdingOf_byIdThenName_neverSpending() {
        assertSame(axis, holdingOf(expenses[0], holdings))
        assertSame(fd, holdingOf(expenses[4], holdings))
        assertNull(holdingOf(expenses[5], holdings))
        assertNull(holdingOf(expenses[6], holdings))
        assertSame(axis, holdingOf(expenses[0].copy(holdingId = "gone"), holdings))
        assertNull(holdingOf(expenses[5].copy(holdingId = "gone"), holdings))
    }

    @Test fun portfolio_worthAndGain() {
        val p = portfolio(holdings, expenses, today)
        val (first, second) = p.rows
        assertSame(fd, first.holding) // the bigger one first
        assertEquals(102000.0, first.invested, 0.0)
        assertEquals(107000.0, first.value, 0.0) // 105,000 on 30 Sep, plus 2,000 on 12 Oct
        assertEquals(5000.0, first.gain!!, 0.0)
        assertSame(axis, second.holding)
        assertEquals(15000.0, second.invested, 0.0)
        assertEquals(15000.0, second.value, 0.0)
        assertNull(second.gain) // no value given: nothing to compare with
        assertEquals(listOf(expenses[5]), p.other)
        assertEquals(3000.0, p.otherInvested, 0.0)
        assertEquals(120000.0, p.invested, 0.0)
        assertEquals(125000.0, p.value, 0.0)
        assertEquals(5000.0, p.gain!!, 0.0)
        assertEquals(5000.0 / 102000 * 100, p.gainPct!!, 1e-9) // against the valued holdings only
        assertEquals(10000.0, p.thisMonth, 0.0)
        assertEquals(120000.0, p.thisYear, 0.0)
    }

    @Test fun portfolio_sip() {
        val p = portfolio(holdings, expenses, today)
        val a = p.rows.first { it.holding === axis }
        assertEquals(5000.0, a.sip!!.amount, 0.0)
        assertEquals("2026-11-05", a.sip!!.due) // October's is in
        assertEquals(5000.0, p.monthly, 0.0)
        val stopped = expenses.take(2) + sip("2026-10-05", repeat = "stopped")
        assertNull(portfolio(holdings, stopped, today).rows.first { it.holding === axis }.sip)
    }

    @Test fun portfolio_tiesByName_andNothingAtAll() {
        val a = Holding("a", "beta", "mf", null, null, null)
        val b = Holding("b", "Alpha", "mf", null, null, null)
        assertEquals(listOf("Alpha", "beta"), portfolio(listOf(a, b), emptyList(), today).rows.map { it.holding.name })
        val empty = portfolio(emptyList(), emptyList(), today)
        assertTrue(empty.isEmpty(emptyList()))
        assertNull(empty.gain)
        assertNull(empty.gainPct)
        // a value with no date doesn't count: worth is what was put in
        val noDate = Holding("h", "Gold", "gold", 999.0, null, null)
        assertEquals(10.0, portfolio(listOf(noDate), listOf(ex(10.0, INVEST, "2026-10-01", "Gold")), today).value, 0.0)
    }

    @Test fun goalsCountLinkedHoldings() {
        val linked = linkedToGoals(portfolio(holdings, expenses, today).rows)
        assertEquals(mapOf("g1" to 15000.0), linked)
        val goal = withLinked(Goal("g1", "House", 50000.0, "2027-12", 5000.0), linked)
        assertEquals(20000.0, goal.saved, 0.0)
        assertEquals(10.0, withLinked(Goal("g2", "x", 100.0, "2027-01", 10.0), linked).saved, 0.0)
    }

    @Test fun afterDebts_openOnly() {
        fun d(amount: Double, type: String, settled: Boolean = false) = Debt("$amount$type", amount, type, "A", "", "", settled, "", emptyList())
        val a = afterDebts(125000.0, listOf(d(2000.0, THEY_OWE), d(7000.0, I_OWE), d(500.0, I_OWE, settled = true)))
        assertEquals(AfterDebts(125000.0, 2000.0, 7000.0, 120000.0), a)
        assertTrue(a.shown())
        assertEquals("Investments, plus ₹ 2000 owed to you, less ₹ 7000 you owe. Bank balances aren't counted.", a.note(whole))
        assertEquals("−₹ 5", AfterDebts(0.0, 0.0, 5.0, -5.0).totalText(whole))
        assertFalse(afterDebts(1.0, emptyList()).shown())
    }

    @Test fun kinds() {
        assertEquals("other", kindOf("crypto").id)
        assertEquals("PPF", kindOf("ppf").label)
        assertEquals(listOf("mf", "stocks", "fd", "rd", "ppf", "nps", "epf", "gold", "other"), HOLDING_KINDS.map { it.id })
    }

    @Test fun wording() {
        val p = portfolio(holdings, expenses, today)
        assertEquals("₹ 120000 put in · +₹ 5000 (+4.9%) gain", p.worthSub(whole))
        assertEquals("₹ 10000 put in this October · ₹ 5000 a month in SIPs", p.monthSub("October", whole))
        val (fdRow, axisRow) = p.rows
        assertEquals("Mutual fund · ₹ 5000 a month · for House", axisRow.sub(listOf(Goal("g1", "House", 1.0, "2027-01", 0.0)), whole))
        assertEquals("Fixed deposit", fdRow.sub(emptyList(), whole))
        assertEquals("", portfolio(listOf(Holding("p", " ppf ", "ppf", null, null, null)), emptyList(), today).rows[0].sub(emptyList(), whole))
        assertEquals("+₹ 5000 gain", fdRow.gainText(whole))
        assertEquals("₹ 15000 put in", axisRow.gainText(whole))
        assertEquals("Nothing put in yet", portfolio(listOf(axis), emptyList(), today).rows[0].gainText(whole))
        val loss = portfolio(listOf(fd.copy(value = 90000.0)), expenses, today)
        assertEquals("−₹ 10000 loss", loss.rows[0].gainText(whole)) // 92,000 now against 102,000 in
        assertEquals("₹ 102000 put in · −₹ 10000 (−9.8%) loss", portfolio(listOf(fd.copy(value = 90000.0)), expenses.filter { holdingOf(it, holdings) === fd }, today).worthSub(whole))
        val day = LocalDate.parse(today)
        assertEquals("Axis Bluechip", entryTitle(expenses[0], holdings))
        assertEquals("5 Oct · Every month", entryMeta(expenses[2], holdings, day))
        assertEquals("Gold coin", entryTitle(expenses[5], holdings))
        assertEquals("2 Oct · Not in a holding", entryMeta(expenses[5], holdings, day))
        assertEquals("Investment", entryTitle(ex(1.0, INVEST, today, "-"), holdings))
    }

    @Test fun putMoneyIn_checks() {
        fun msg(h: Holding?, amount: String, date: String = today, placeholder: String = "") =
            (checkInvestEntry(h, amount, placeholder, date, "UPI", "", false, false) as? Checked.Invalid)?.message
        assertEquals("Pick a holding first, or add a new one.", msg(null, "5"))
        assertEquals("Enter the amount you put in.", msg(axis, ""))
        assertEquals("Enter the amount you put in.", msg(axis, "0"))
        assertEquals("Pick the date.", msg(axis, "5", date = ""))
        // an empty amount uses the SIP's; amounts aren't rounded, as parseFloat
        val ok = checkInvestEntry(axis, "", "5000", today, "Net Banking", " n ", repeat = true, wasSip = true) as Checked.Ok
        assertEquals(InvestEntryInput(5000.0, today, "Net Banking", "n", axis, "monthly"), ok.value)
        assertEquals("stopped", (checkInvestEntry(axis, "1.005", "", today, "UPI", "", repeat = false, wasSip = true) as Checked.Ok).value.repeat)
        assertEquals(1.005, (checkInvestEntry(axis, "1.005", "", today, "UPI", "", false, false) as Checked.Ok).value.amount, 0.0)
        assertNull((checkInvestEntry(axis, "1", "", today, "UPI", "", false, false) as Checked.Ok).value.repeat)
    }

    @Test fun holding_checks() {
        fun msg(name: String, value: String = "", editing: Holding? = null) =
            (checkHolding(name, "mf", value, "", editing, holdings, today) as? Checked.Invalid)?.message
        assertEquals("Give it a name, like the fund or the bank.", msg("  "))
        assertEquals("You already have a holding with that name.", msg(" sbi fd "))
        assertNull(msg("SBI FD", editing = fd)) // its own name
        assertEquals("Enter what it is worth as a number, or leave it empty.", msg("New", "abc"))
        // a changed value is dated today; an unchanged one keeps its date; none clears both
        assertEquals(HoldingFields("SBI FD", "mf", 105000.0, "2026-09-30", null), (checkHolding("SBI FD", "mf", "1,05,000", "", fd, holdings, today) as Checked.Ok).value)
        assertEquals("2026-10-24", (checkHolding("SBI FD", "fd", "110000.456", "", fd, holdings, today) as Checked.Ok).value.valueDate)
        assertEquals(110000.46, (checkHolding("SBI FD", "fd", "110000.456", "", fd, holdings, today) as Checked.Ok).value.value!!, 0.0)
        assertEquals(HoldingFields("New", "gold", null, null, "g1"), (checkHolding(" New ", "gold", "", "g1", null, holdings, today) as Checked.Ok).value)
    }

    @Test fun entry_checks() {
        assertEquals("Enter the amount as a number.", (checkEntryEdit("0", today, null) as Checked.Invalid).message)
        assertEquals("Enter the amount as a number.", (checkEntryEdit("x", today, null) as Checked.Invalid).message)
        assertEquals("Pick the date.", (checkEntryEdit("5", "", null) as Checked.Invalid).message)
        assertEquals(EntryEdit(12.35, today, fd), (checkEntryEdit("12.345", today, fd) as Checked.Ok).value)
        assertEquals(1268.0, investNum("₹1,268")!!, 0.0)
    }

    @Test fun forecast_leavesSipsOut() {
        val rent = ex(18000.0, "Home & Rent", "2026-09-28", "Rent", repeat = "monthly")
        val f = forecast(listOf(rent, sip("2026-09-05")), "2026-10-04")
        assertEquals(listOf("Rent"), f.billsDue.map { it.title })
        assertEquals(1, f.next.bills)
        assertEquals(18000.0, f.next.fixed, 0.0)
    }
}
