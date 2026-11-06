package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Ported from tests/budget.test.mjs.
class BudgetSuggestTest {
    private val today = "2026-10-25"
    private fun exp(date: String, category: String, amount: Double, repeat: String? = null) =
        Expense("e$date$category", amount, category, date, "UPI", "", "secret note", "", emptyList(), repeat = repeat)
    private fun inc(date: String, amount: Double) = Income("i$date", amount, date, "Salary", "Online", "", "")
    private val income = listOf(inc("2026-07-01", 60000.0), inc("2026-08-01", 60000.0), inc("2026-09-01", 60000.0), inc("2026-10-01", 99999.0))
    private val expenses = listOf("07", "08", "09").flatMap { m ->
        listOf(
            exp("2026-$m-02", "Home & Rent", 15000.0, "monthly"),
            exp("2026-$m-10", "Food & Dining", 12000.0),
            exp("2026-$m-12", "Shopping", 15000.0),
            exp("2026-$m-15", "Groceries", 8000.0),
            exp("2026-$m-20", "Savings & Investments", 5000.0),
        )
    } + exp("2026-10-03", "Shopping", 50000.0) // this month: not counted
    private val goals = listOf(Goal("g", "Goa", 30000.0, "2027-02", 0.0))
    private val facts get() = budgetFacts(expenses, income, goals, today)!!

    @Test fun `facts are monthly averages of the last three whole months, investments apart`() {
        val f = facts
        assertEquals(3, f.months)
        assertEquals(60000.0, f.income, 0.0)
        assertEquals(50000.0, f.spent, 0.0)
        assertEquals(5000.0, f.invested, 0.0)
        assertEquals(mapOf("Home & Rent" to 15000.0, "Food & Dining" to 12000.0, "Shopping" to 15000.0, "Groceries" to 8000.0), f.cats)
        assertEquals(mapOf("Home & Rent" to 15000.0), f.fixed)
        assertEquals(6000.0, f.goalsMonthly, 0.0)
    }

    @Test fun `what the AI sees is numbers only`() {
        val text = factsLines(facts, "₹")
        assertTrue(text.contains("Income: ₹60000 a month on average"))
        assertTrue(text.contains("Home & Rent: ₹15000 (of which ₹15000 repeating"))
        assertFalse(budgetPrompt(facts, currencyOf("INR")).toList().any { it.contains("secret") }) // entries never go
        assertTrue(text.contains("Savings goals, in the user's words:\n- Goa: ₹30000 by Feb 2027, ₹0 saved")) // goals do, by name
    }

    @Test fun `no income in the last three months, no suggestion`() {
        assertNull(budgetFacts(expenses, listOf(inc("2026-10-01", 5.0)), emptyList(), today))
    }

    @Test fun `the on-device plan keeps a fifth of income, trimming flexible spending, never the bills`() {
        val s = suggestFromFacts(facts)!!
        assertEquals(48000.0, s.total, 0.0)
        assertEquals(12000.0, s.saves, 0.0)
        assertEquals(15000.0, s.cats.getValue("Home & Rent"), 0.0)
        assertTrue(s.cats.getValue("Shopping") < 15000 && s.cats.getValue("Food & Dining") < 12000)
        assertTrue(s.cats.values.sum() <= s.total)
    }

    @Test fun `the AI's answer is checked`() {
        val raw = readReply("""{"total": 90000, "cats": {"Home & Rent": 9000, "Shopping": "8020", "Crypto": 5000, "Health": 3000}, "tip": "Cut  shopping."}""")
        val s = checkReply(raw, facts)!!
        assertEquals(mapOf("Home & Rent" to 15000.0, "Shopping" to 8100.0), s.cats)
        assertEquals(60000.0, s.total, 0.0)
        assertEquals("Cut shopping.", s.tip)
        assertNull(checkReply(readReply("""{"total": "lots"}"""), facts))
    }

    @Test fun `reads JSON from a chatty reply`() {
        assertEquals("40000", readReply("Sure! ```json\n{\"total\": 40000, \"cats\": {}}\n```")!!["total"].toString())
        assertNull(readReply("no idea"))
    }

    @Test fun `the line under the button`() {
        val f = facts
        val (keeps, rest) = suggestionText(suggestFromFacts(f)!!, f, fromAi = false) { "₹ ${it.toLong()}" }
        assertEquals("Keeps ₹ 12000 a month (20%) of your ₹ 60000 average income.", keeps)
        assertEquals("Worked out on your device. Change anything, then save.", rest)
    }

    @Test fun `one AI budget a month`() {
        assertFalse(aiBudgetUsed(null, "2026-10-29"))
        assertTrue(aiBudgetUsed("2026-10", "2026-10-29"))
        assertFalse(aiBudgetUsed("2026-09", "2026-10-01"))
        assertFalse(aiBudgetUsed("2025-10", "2026-10-15"))
    }

    @Test fun `an early rent payment counts once`() {
        val rent = listOf("2026-08-01", "2026-09-01", "2026-10-01", "2026-10-30").map { exp(it, "Home & Rent", 9500.0, "monthly") }
        val f = budgetFacts(rent, income, emptyList(), "2026-11-02")!!
        assertEquals(9500.0, f.cats.getValue("Home & Rent"), 0.0)
        assertEquals(9500.0, f.fixed.getValue("Home & Rent"), 0.0)
        assertEquals(9500.0, f.spent, 0.0)
    }

    @Test fun `a repeating bill counts at its latest amount`() {
        val bills = listOf(exp("2026-08-05", "Bills & Utilities", 800.0, "monthly"), exp("2026-10-05", "Bills & Utilities", 1000.0, "monthly"))
        assertEquals(1000.0, budgetFacts(bills, income, emptyList(), "2026-11-02")!!.fixed.getValue("Bills & Utilities"), 0.0)
    }

    @Test fun `one-off spending stays out of the typical month and is named as a number`() {
        val trips = listOf(exp("2026-08-14", "Travel", 28000.0), exp("2026-09-10", "Travel", 2000.0), exp("2026-10-10", "Travel", 2000.0))
        val f = budgetFacts(trips, income, emptyList(), "2026-11-02")!!
        assertEquals(2000.0, f.cats.getValue("Travel"), 0.0)
        assertEquals(8667.0, f.oneOffs.getValue("Travel"), 1.0)
        assertTrue(factsLines(f, "₹").contains("One-off spending on top of that, averaged:\n- Travel: ₹8667 a month"))
        assertFalse(factsLines(facts, "₹").contains("One-off"))
    }

    @Test fun `a one-time category gets no limit`() {
        val f = budgetFacts(listOf(exp("2026-09-03", "Other", 5000.0)), income, emptyList(), "2026-11-02")!!
        assertFalse("Other" in f.cats)
        assertTrue("Other" in f.oneOffs)
    }

    private val goodCats = mapOf("Home & Rent" to 15000.0, "Food & Dining" to 9000.0, "Shopping" to 8000.0, "Groceries" to 8000.0)
    private fun plan(total: Double, cats: Map<String, Double> = goodCats) = checkSuggestion(total, cats.toList(), null, facts.copy(goalsMonthly = 0.0))!!
    private val noGoals get() = facts.copy(goalsMonthly = 0.0)

    @Test fun `a sound AI plan holds`() = assertTrue(aiPlanHolds(plan(40000.0), noGoals))

    @Test fun `an AI plan that misses the saving aim does not hold`() {
        assertFalse(aiPlanHolds(plan(55000.0), noGoals))
        assertFalse(aiPlanHolds(plan(90000.0), noGoals)) // capped at income, keeps nothing
    }

    @Test fun `an AI plan with limits that do not add up does not hold`() {
        assertFalse(aiPlanHolds(plan(40000.0, mapOf("Home & Rent" to 15000.0, "Shopping" to 5000.0)), noGoals))
        assertFalse(aiPlanHolds(plan(40000.0, emptyMap()), noGoals))
    }

    @Test fun `an AI plan that skips a bill or raises a category does not hold`() {
        assertFalse(aiPlanHolds(plan(25000.0, goodCats - "Home & Rent"), noGoals))
        assertFalse(aiPlanHolds(plan(48000.0, goodCats + ("Shopping" to 17000.0)), noGoals))
    }

    @Test fun `goals raise what must be kept`() {
        val big = noGoals.copy(goalsMonthly = 25000.0)
        assertFalse(aiPlanHolds(checkSuggestion(40000.0, goodCats.toList(), null, big)!!, big))
    }

    @Test fun `the AI sees the month and the same month last year`() {
        val withLastYear = expenses + exp("2025-10-20", "Shopping", 30000.0) + exp("2025-10-21", "Travel", 9000.0) + exp("2025-10-22", "Savings & Investments", 1.0)
        val f = budgetFacts(withLastYear, income, goals, today)!!
        assertEquals("2026-10", f.month)
        assertEquals(mapOf("Shopping" to 30000.0, "Travel" to 9000.0), f.lastYear)
        val text = factsLines(f, "₹")
        assertTrue(text.contains("Budget for: October 2026"))
        assertTrue(text.contains("The same month last year, by category:\n- Travel: ₹9000\n- Shopping: ₹30000"))
        // Last year's festive month lets Shopping go past its typical month, and Travel come back
        val festive = f.copy(goalsMonthly = 0.0)
        val p = checkSuggestion(48000.0, (goodCats + ("Shopping" to 20000.0) + ("Food & Dining" to 5000.0)).toList(), null, festive)!!
        assertTrue(aiPlanHolds(p, festive))
        assertTrue("Travel" in checkSuggestion(48000.0, listOf("Travel" to 5000.0), null, festive)!!.cats)
    }

    @Test fun `the user's plans go to the AI and let a plan stretch`() {
        val (_, user) = budgetPrompt(facts, currencyOf("INR"), cleanPlans("  Goa trip\n new phone "))
        assertTrue(user.endsWith("The user's plans for this month, in their words: Goa trip new phone"))
        assertEquals(PLANS_MAX, cleanPlans("x".repeat(500)).length)
        assertFalse("Travel" in checkSuggestion(48000.0, listOf("Travel" to 5000.0), null, noGoals)!!.cats)
        val trip = checkSuggestion(60000.0, (goodCats + ("Travel" to 20000.0)).toList(), null, noGoals, allowNew = true)!!
        assertFalse(aiPlanHolds(trip, noGoals))
        assertTrue(aiPlanHolds(trip, noGoals, hasPlans = true))
        assertFalse(aiPlanHolds(plan(90000.0, goodCats - "Home & Rent"), noGoals, hasPlans = true)) // bills still covered
    }

    @Test fun `a goal due this month counts as a plan, and holidays reach the AI`() {
        assertFalse(hasPlans(facts, ""))
        val due = budgetFacts(expenses, income, listOf(Goal("g", "New phone", 40000.0, "2026-10", 10000.0), Goal("d", "Done", 10.0, "2027-01", 10.0)), today)!!
        assertEquals(listOf("New phone"), due.goals.map { it.name }) // reached goals are left out
        assertTrue(hasPlans(due, ""))
        assertTrue(factsLines(due, "₹").contains("- New phone: ₹40000 by Oct 2026, ₹10000 saved (due this month)"))
        val (_, user) = budgetPrompt(facts, currencyOf("INR"), holidays = listOf("20 Oct: Dussehra"))
        assertTrue(user.contains("Festivals and holidays this month, from the public calendar:\n- 20 Oct: Dussehra"))
    }

    @Test fun `the device plan always holds`() = assertTrue(aiPlanHolds(suggestFromFacts(noGoals)!!, noGoals))

    @Test fun `the tip ends as a sentence`() {
        assertEquals("Cut travel and shopping.", checkSuggestion(40000.0, goodCats.toList(), "Cut travel and shopping", noGoals)!!.tip)
        assertEquals("Cut travel!", checkSuggestion(40000.0, goodCats.toList(), "Cut travel!", noGoals)!!.tip)
        assertEquals("", checkSuggestion(40000.0, goodCats.toList(), null, noGoals)!!.tip)
    }

    @Test fun `a stored plan reads back the same`() {
        val s = checkSuggestion(40000.0, goodCats.toList(), "Cut shopping.", noGoals)!!
        assertEquals(s, checkReply(readReply(planJson(s)), noGoals))
    }

    @Test fun `only real bills are blamed for keeping nothing`() {
        val money = { n: Double -> "₹${n.toLong()}" }
        val s = BudgetSuggestion(60000.0, emptyMap(), "", 0.0)
        assertEquals("This uses all of your ₹60000 average income.", suggestionText(s, noGoals, true, money = money).first)
        val heavy = noGoals.copy(fixed = mapOf("Home & Rent" to 60000.0))
        assertTrue(suggestionText(s, heavy, true, money = money).first.startsWith("Your bills take all"))
    }

    @Test fun `a saved plan says so`() {
        val text = suggestionText(plan(40000.0), noGoals, fromAi = true, saved = true) { it.toString() }.second
        assertEquals("Your suggestion from earlier this month. Change anything, then save.", text)
    }
}
