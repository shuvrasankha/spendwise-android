package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The assistant: question → route → query → figures, end to end over entries, with no AI in the way. */
class AssistantTest {
    private val today = "2026-11-02" // a Monday
    private val money = { n: Double -> "₹" + String.format(java.util.Locale.US, "%,.2f", n).removeSuffix(".00") }

    private val rent = (1..11).map { m -> ex(8000.0, "Home & Rent", "2026-${m.toString().padStart(2, '0')}-01", "House rent") }
    private val expenses = rent + listOf(
        ex(3000.0, FOOD, "2026-10-05", "Dinner"),
        ex(1500.0, FOOD, "2026-10-05", "Lunch"),
        ex(1200.0, "Shopping", "2026-10-20", "Shoes"),
        ex(600.0, FOOD, "2026-09-10", "Swiggy"),
        ex(400.0, FOOD, "2026-11-01", "Swiggy"),
        ex(5000.0, INVEST, "2026-10-07", "SIP"),
        ex(9999.0, "Shopping", "2026-12-25", "Future gift"),
        ex(700.0, "", "2026-10-09", "No category"),
    )
    private val income = listOf(
        Income("i1", 50000.0, "2026-10-01", "Salary", "", "", ""),
        Income("i2", 500.0, "2026-10-21", "Refund", "", "", "shoes returned"),
    )

    private fun route(q: String, ctx: AssistantContext = AssistantContext()) = routeQuestion(q, today, ctx)
    private fun query(q: String, ctx: AssistantContext = AssistantContext()) = (route(q, ctx) as AssistantRoute.Calculate).query
    private fun card(q: String, ctx: AssistantContext = AssistantContext(), e: List<Expense> = expenses, i: List<Income> = income) =
        calculate(query(q, ctx), e, i, today, money)
    private fun fact(c: FinCard, label: String) = c.facts.first { it.label == label }.value

    // ── Dates ──

    @Test fun datesFromWords() {
        fun d(q: String) = datesIn(" $q ", today)
        assertEquals("2026-11-02" to "2026-11-02", d("today"))
        assertEquals("2026-11-01" to "2026-11-01", d("yesterday"))
        assertEquals("2026-11-03" to "2026-11-03", d("tomorrow"))
        assertEquals("2026-11-02" to today, d("this week"))
        assertEquals("2026-10-26" to "2026-11-01", d("last week"))
        assertEquals("2026-11-01" to today, d("this month"))
        assertEquals("2026-10-01" to "2026-10-31", d("last month"))
        assertEquals("2026-01-01" to today, d("this year"))
        assertEquals("2025-01-01" to "2025-12-31", d("last year"))
        assertEquals("2026-10-27" to today, d("last 7 days"))
        assertEquals("2026-10-04" to today, d("last 30 days"))
        assertEquals("2026-08-05" to today, d("last 90 days"))
    }

    @Test fun lastThreeMonthsIsNotAllTime() {
        // It used to match nothing and silently search everything
        assertEquals("2026-09-01" to today, datesIn(" shopping in the last 3 months ", today))
        assertEquals("2026-09-01" to today, datesIn(" past three months ", today))
        assertEquals("2026-10-01" to "2026-10-31", datesIn(" last 1 month ", today))
    }

    @Test fun customRanges() {
        fun d(q: String) = datesIn(" $q ", today)
        assertEquals("2026-09-01" to "2026-09-15", d("from 1 sep to 15 sep"))
        assertEquals("2026-01-01" to "2026-02-01", d("between 2026-01-01 and 2026-02-01"))
        assertEquals("2025-12-20" to "2026-01-05", d("20 dec to 5 jan"))
        assertEquals("2026-01-01" to "2026-03-31", d("january to march"))
        assertEquals("2026-09-05" to "2026-09-05", d("on 5 sep"))
        assertEquals("2025-12-05" to "2025-12-05", d("on 5 dec")) // the most recent past one
    }

    @Test fun anImpossibleDateIsNotGuessed() {
        for (q in listOf("food from 31 feb to 3 mar", "food on 31 feb", "food from 1 jan to 30 feb")) {
            val r = route(q)
            assertTrue(q, r is AssistantRoute.Clarify && r.text.contains("isn't a date"))
        }
        assertTrue(route("food from 1 feb to 28 feb") is AssistantRoute.Calculate)
    }

    @Test fun calendarYearUnlessFinancialYearIsNamed() {
        assertEquals("2026-01-01" to today, datesIn(" this year ", today))
        assertEquals("2026-04-01" to today, datesIn(" this financial year ", today))
        assertEquals("2025-04-01" to "2026-03-31", datesIn(" last financial year ", today))
        assertEquals("2024-04-01" to "2025-03-31", datesIn(" fy 2024-25 ", today))
        // January–March still belongs to the year that began the April before
        assertEquals("2025-04-01" to "2026-02-10", datesIn(" this fy ", "2026-02-10"))
    }

    @Test fun amountsAndVerbsAreNotDates() {
        assertEquals(null to null, datesIn(" how much did i spend on ₹2000 items ", today))
        assertEquals(null to null, datesIn(" how much may i spend ", today))
        assertEquals("2026-05-01" to "2026-05-31", datesIn(" rent in may ", today))
    }

    // ── Averages ──

    @Test fun dailyAverageUsesCalendarDays() {
        val c = card("What is my average daily spending for last month?")
        // October: 8000 rent + 4500 + 1200 + 700 = 14400 over 31 days; investments are out
        assertEquals(money(14400.0 / 31), c.headline)
        assertEquals("31", fact(c, "Calendar days"))
        assertTrue(c.basis, c.basis.contains("31 calendar days"))
    }

    @Test fun anAverageWithNoKindSaidIsPerCalendarDay() {
        assertEquals(FinMetric.DAILY_AVG, query("average spending last month").metric)
    }

    @Test fun activeDaysAreOnlyUsedWhenAskedFor() {
        val c = card("average on days I spent last month")
        // Spending on 1, 5, 9 and 20 October
        assertEquals(money(14400.0 / 4), c.headline)
        assertEquals("4 of 31", fact(c, "Days with an entry"))
    }

    @Test fun partialMonthAveragesOnlyElapsedDays() {
        val c = card("average daily spending this month")
        assertEquals(money(8400.0 / 2), c.headline) // 2 days in, not 30
    }

    @Test fun monthlyAverageOfOneMonthIsItsTotal() {
        assertEquals(money(14400.0), card("What was my average monthly spending last month?").headline)
    }

    @Test fun monthlyAverageSaysHowItTreatsAMonthInProgress() {
        val c = card("average monthly rent this year")
        assertEquals(money(8000.0), c.headline) // 11 payments over 11 months touched
        assertEquals("11", fact(c, "Months"))
        assertTrue(c.basis, c.basis.contains("current month as a whole one"))
    }

    @Test fun leapYears() {
        val feb = listOf(ex(2900.0, FOOD, "2024-02-29", "Leap dinner"))
        val q = routeQuestion("average daily food spending from 2024-02-01 to 2024-02-29", today) as AssistantRoute.Calculate
        val c = calculate(q.query, feb, emptyList(), today, money)
        assertEquals(money(100.0), c.headline)
        assertEquals("29", fact(c, "Calendar days"))
        // On 29 Feb, the month before ran 31 days; last month is still whole
        assertEquals("2024-01-01" to "2024-01-31", datesIn(" last month ", "2024-02-29"))
        assertEquals("2024-02-01" to "2024-02-29", datesIn(" last month ", "2024-03-15"))
    }

    // ── Categories and totals ──

    @Test fun houseRentThisYear() {
        val q = query("How much did I spend on house rent this year?")
        assertEquals(listOf("Home & Rent"), q.categories)
        assertNull(q.text) // "house" is part of the category, not a word to search for
        val c = card("How much did I spend on house rent this year?")
        assertEquals(money(88000.0), c.headline)
        assertEquals("11 expenses", c.caption)
        assertEquals(money(8000.0), fact(c, "Monthly average"))
        assertEquals(money(8000.0), fact(c, "Highest"))
        assertEquals("1 Jan 2026 – 2 Nov 2026", fact(c, "Period"))
    }

    @Test fun foodThisMonth() {
        assertEquals(money(400.0), card("How much did I spend on food this month?").headline)
    }

    @Test fun categoryWordsVary() {
        for (q in listOf("housing costs this month", "rent this month", "HOUSE RENT this month")) assertEquals(q, listOf("Home & Rent"), query(q).categories)
        assertEquals(listOf("Food & Dining"), query("eating out this month").categories)
        assertEquals(listOf("Transport"), query("taxis this month").categories)
    }

    @Test fun anOldCategoryNameCountsUnderItsNewOne() {
        val old = listOf(ex(1000.0, "Rent", "2026-10-01"))
        assertEquals(money(1000.0), card("rent last month", e = old).headline)
    }

    @Test fun missingCategoryCountsAsOtherAndInTotals() {
        assertEquals(money(700.0), card("uncategorized spending last month").headline)
        assertTrue(card("total spent last month").headline == money(14400.0))
    }

    @Test fun lastThreeMonthsOfShopping() {
        // The future gift isn't counted, and neither is anything before September
        assertEquals(money(1200.0), card("How much did I spend on shopping in the last 3 months?").headline)
    }

    @Test fun biggestExpenseThisYear() {
        val c = card("What is my biggest expense this year?")
        assertEquals(money(8000.0), c.headline) // not the 9,999 dated in December
    }

    // ── Refunds, investments, odd amounts ──

    @Test fun refundsAreIncomeAndTheCardSaysSo() {
        val c = card("total spent last month")
        assertEquals(money(14400.0), c.headline)
        assertTrue(c.basis, c.basis.contains("Refunds of ${money(500.0)} are recorded as income"))
        assertEquals(money(500.0), card("how much did I get in refunds last month").headline)
        assertFalse(card("rent last month").basis.contains("Refunds"))
        assertFalse(card("biggest expense last month").basis.contains("Refunds"))
    }

    @Test fun investmentsAreNotSpendingUnlessAskedFor() {
        assertEquals(money(5000.0), card("how much did I invest last month").headline)
    }

    @Test fun aNegativeEntryNetsOffAndAZeroOneChangesNothing() {
        val odd = listOf(ex(1000.0, FOOD, "2026-10-01", "Meal"), ex(-200.0, FOOD, "2026-10-02", "Reversed"), ex(0.0, FOOD, "2026-10-03", "Free"))
        assertEquals(money(800.0), card("food last month", e = odd).headline)
        // A day with only a reversal or a zero isn't a day of spending
        assertEquals("1 of 31", fact(card("average on days I spent on food last month", e = odd), "Days with an entry"))
    }

    @Test fun aRepeatingBillCountsOnlyItsRealEntries() {
        val bills = listOf(ex(500.0, "Bills & Utilities", "2026-10-03", "Internet", repeat = "monthly"))
        assertEquals(money(500.0), card("bills last month", e = bills).headline)
        assertEquals(money(0.0), card("bills this month", e = bills).headline) // not yet paid, so not projected
    }

    @Test fun futureAndMalformedDatesAreLeftOut() {
        val odd = listOf(ex(100.0, FOOD, "2026-10-01"), ex(50.0, FOOD, "not-a-date"), ex(70.0, FOOD, ""), ex(90.0, FOOD, "2027-01-01"))
        assertEquals(money(100.0), card("all my food spending", e = odd).headline)
    }

    @Test fun aFutureDayIsSaidPlainly() {
        val c = card("how much did I spend tomorrow")
        assertEquals(money(0.0), c.headline)
        assertTrue(c.caption!!.contains("hasn't come yet"))
    }

    // ── Comparisons ──

    @Test fun compareThisMonthWithLastMonth() {
        val q = query("Compare my spending this month with last month")
        assertEquals(FinMetric.COMPARE, q.metric)
        assertEquals(listOf("2026-11-01", today, "2026-10-01", "2026-10-31"), listOf(q.from, q.to, q.otherFrom, q.otherTo))
        val c = card("Compare my spending this month with last month")
        assertEquals(money(8400.0), c.headline)
        assertEquals(money(14400.0), fact(c, "October 2026".removeSuffix(" 2026")))
        assertEquals("Down ${jsToFixed((14400 - 8400) / 14400.0 * 100, 0)}% (${money(6000.0)} less)", c.changeText)
        assertFalse(c.up)
        // A month in progress against a whole one: the fair figure is there too (1–2 Oct: 8000)
        assertTrue(fact(c, "Same days of October").startsWith(money(8000.0)))
    }

    @Test fun compareWithNothingBefore() {
        val c = card("compare food this month with last month", e = listOf(ex(100.0, FOOD, "2026-11-01")))
        assertEquals("Nothing recorded in October to compare with", c.changeText)
    }

    @Test fun oneNamedPeriodIsComparedWithItsNeighbour() {
        val now = query("compare this month")
        assertEquals("2026-10-01" to "2026-10-31", now.otherFrom to now.otherTo)
        val past = query("compare last month")
        assertEquals("2026-10-01" to "2026-11-02", past.otherFrom!!.let { it to past.to })
    }

    @Test fun theBiggestIncrease() {
        val c = card("Which category has increased the most?")
        // 1–2 Nov against 1–2 Oct, the same days: Food 400 vs 0
        assertEquals(money(400.0), c.headline)
        assertEquals("more on Food & Dining", c.caption)
        assertEquals("New: nothing before", c.changeText)
        assertTrue(c.up)
    }

    @Test fun savedIsIncomeMinusSpending() {
        val c = card("How much did I save last month?")
        assertEquals(money(50500.0 - 14400), c.headline)
        assertFalse(c.up)
    }

    @Test fun savedWithoutIncomeSaysWhy() {
        val c = card("How much did I save last month?", i = emptyList())
        assertEquals("—", c.headline)
        assertTrue(c.caption!!.contains("No income recorded"))
    }

    @Test fun topCategoryAndBreakdown() {
        val top = card("Which category did I spend the most on last month?")
        assertEquals(money(8000.0), top.headline)
        assertTrue(top.caption!!.startsWith("Home & Rent"))
        val all = card("Spending by category last month")
        assertEquals(money(14400.0), all.headline)
        assertEquals("Home & Rent", all.rows.first().title)
    }

    // ── Empty data ──

    @Test fun emptyHistoryNeverDividesByZero() {
        for (q in listOf(
            "average daily spending last month", "average monthly spending last month", "average on days I spent last month",
            "total spent this year", "biggest expense this year", "compare this month with last month",
            "which category has increased the most", "spending by category this year", "how much did I save this month",
        )) {
            val c = card(q, e = emptyList(), i = emptyList())
            assertFalse(q, c.headline.contains("NaN") || c.headline.contains("Infinity"))
        }
        assertEquals(money(0.0), card("average daily spending last month", e = emptyList()).headline)
    }

    // ── Routing, ambiguity and follow-ups ──

    @Test fun adviceGoesToTheCoach() {
        fun topic(q: String) = (route(q) as AssistantRoute.Advise).topic
        assertEquals(CoachTopic.SPENDING, topic("Where can I reduce my spending?"))
        assertEquals(CoachTopic.SPENDING, topic("Am I spending too much on food?"))
        assertEquals(CoachTopic.SAVINGS, topic("How can I save ₹5,000 next month?"))
        assertEquals(CoachTopic.SPENDING, topic("Analyze my spending habits"))
        assertEquals(CoachTopic.BUDGET, topic("Suggest a monthly budget based on my spending history"))
        assertEquals(CoachTopic.SPENDING, topic("What financial habits should I improve?"))
        assertEquals(CoachTopic.DEBT, topic("Should I pay my debts before saving?"))
        assertEquals(CoachTopic.TRENDS, topic("Why did I spend more last month?"))
    }

    @Test fun factualQuestionsDontGoToTheCoach() {
        for (q in listOf(
            "How much did I save this month?", "What is my biggest expense this year?",
            "Compare my spending this month with last month", "How much did I spend on food this month?",
            "Which category has increased the most?", "What was my average monthly spending last month?",
        )) assertTrue(q, route(q) is AssistantRoute.Calculate)
    }

    @Test fun advicePassesOnTheCategoryAndKeepsAmountsOutOfDates() {
        val food = route("Am I spending too much on food?") as AssistantRoute.Advise
        assertEquals(listOf("Food & Dining"), food.categories)
        val save = route("How can I save ₹2000 next month?") as AssistantRoute.Advise
        assertNull(save.from)
    }

    @Test fun adviceFiguresAreCalculatedToo() {
        val r = route("Am I spending too much on food?") as AssistantRoute.Advise
        val range = adviceRange(r.from, r.to, java.time.LocalDate.parse(today))
        assertEquals("2026-09-01", range.start) // the Insights "3 months" card
        val c = calculate(adviceQuery(r, range), expenses, income, today, money)
        assertEquals(money(5500.0), c.headline)
    }

    @Test fun aMissingPeriodIsAskedNotGuessed() {
        val r = route("How much did I spend?") as AssistantRoute.Clarify
        assertEquals(listOf("This month", "This year", "All time"), r.options.map { it.label })
        // Tapping an option is a complete question
        assertEquals(FinMetric.TOTAL, query(r.options[0].question).metric)
        assertEquals("2026-11-01", query(r.options[0].question).from)
        assertNull(query(r.options[2].question).from)
        assertTrue(route("average spending") is AssistantRoute.Clarify)
        assertTrue(route("compare my spending") is AssistantRoute.Clarify)
    }

    @Test fun nonsenseIsAskedAbout() {
        assertTrue(routeQuestion("hello there", today, found = { false }) is AssistantRoute.Advise) // the AI answers what isn't a sum
        assertTrue(route("and last month?") is AssistantRoute.Calculate) // a period alone is enough to add up
        assertTrue(route("and?") is AssistantRoute.Clarify)
    }

    @Test fun followUpsCarryWhatTheyDontName() {
        val first = query("How much did I spend on food this month?")
        val ctx = AssistantContext(first)
        val month = query("and last month?", ctx)
        assertEquals(listOf("Food & Dining"), month.categories)
        assertEquals("2026-10-01" to "2026-10-31", month.from to month.to)
        val shop = query("what about shopping?", ctx)
        assertEquals(listOf("Shopping"), shop.categories)
        assertEquals("2026-11-01", shop.from)
        val avg = query("and the daily average?", ctx)
        assertEquals(FinMetric.DAILY_AVG to listOf("Food & Dining"), avg.metric to avg.categories)
        val cmp = query("compare with last month", ctx)
        assertEquals(listOf("2026-11-01", "2026-10-01", "2026-10-31"), listOf(cmp.from, cmp.otherFrom, cmp.otherTo))
        assertEquals(listOf("Food & Dining"), cmp.categories)
    }

    @Test fun talkingOnWithTheCoachStaysAdvice() {
        val ctx = AssistantContext(lastWasAdvice = true, lastTopic = CoachTopic.SAVINGS)
        assertEquals(CoachTopic.SAVINGS, (route("tell me more about that", ctx) as AssistantRoute.Advise).topic)
        assertTrue(route("what about the second one?", ctx) is AssistantRoute.Advise)
        // A plain sum is still a sum
        assertTrue(route("how much did I spend on food last month", ctx) is AssistantRoute.Calculate)
    }

    // ── The AI's part ──

    @Test fun aSpecFromTheAiCantChangeDatesTheParserFound() {
        val local = FinQuery(FinMetric.TOTAL, text = "blue shirts", from = "2026-10-01", to = "2026-10-31")
        val ai = readSpec("""{"type":"expense","text":"shirts","categories":["Shopping"],"from":"2020-01-01","to":"2020-12-31","measure":"total"}""")!!
        val merged = mergeAiSpec(local, ai)
        assertEquals("2026-10-01" to "2026-10-31", merged.from to merged.to)
        assertEquals(listOf("Shopping"), merged.categories)
    }

    @Test fun anAverageTheParserFoundSurvivesTheAi() {
        val ai = readSpec("""{"type":"expense","measure":"biggest"}""")!!
        assertEquals(FinMetric.DAILY_AVG, mergeAiSpec(FinQuery(FinMetric.DAILY_AVG, from = "2026-10-01", to = "2026-10-31"), ai).metric)
    }

    @Test fun aBadAiReplyIsNoSpecAtAll() {
        assertNull(readSpec("Sorry, I can't help with that."))
        assertNull(readSpec("{not json"))
    }

    @Test fun onlyUnsureQuestionsReachTheAi() {
        assertFalse((route("How much did I spend on food this month?") as AssistantRoute.Calculate).unsure)
        assertFalse((route("average daily spending last month") as AssistantRoute.Calculate).unsure)
        assertTrue((route("blue denim shirts last month") as AssistantRoute.Calculate).unsure)
    }

    @Test fun whatTheCoachIsGivenIsTotalsOnly() {
        val range = adviceRange(null, null, java.time.LocalDate.parse(today))
        val facts = factsText(summarize(expenses, income, emptyList(), range), range, money)
        for (word in listOf("Swiggy", "Dinner", "shoes returned", "Future gift")) assertFalse(word, facts.contains(word))
    }

    @Test fun theCoachIsGivenMonthlyFigures() {
        val range = adviceRange("2026-09-01", "2026-10-31", java.time.LocalDate.parse(today))
        val facts = adviceFacts(summarize(expenses, income, emptyList(), range), range, money)
        // Food over September and October: 600 + 4500 = 5100, so 2550 a month
        assertTrue(facts, facts.contains("PER MONTH, over 2 calendar months"))
        assertTrue(facts, facts.contains("Food & Dining ${money(2550.0)}"))
    }

    @Test fun everyFailureHasWords() {
        for (f in AiFailure.entries) assertTrue(coachErrorText(f, "5:30 AM").isNotEmpty())
        assertTrue(coachErrorText(AiFailure.RATE_LIMITED, "5:30 AM").contains("5:30 AM"))
    }

    // ── Currency ──

    @Test fun figuresAreShownInTheDevicesCurrencyAndNeverConverted() {
        val usd = { n: Double -> "$" + n.toLong() }
        val c = calculate(query("rent last month"), expenses, income, today, usd)
        assertEquals("$8000", c.headline) // amounts carry no currency (spec §2.12): same number, another symbol
    }

    @Test fun aSavedQueryKeepsItsDates() {
        val json = kotlinx.serialization.json.Json.encodeToString(FinQuery.serializer(), query("food last month"))
        assertEquals(query("food last month"), kotlinx.serialization.json.Json.decodeFromString(FinQuery.serializer(), json))
    }

    @Test fun questionsTheEntriesCantAnswerGoToTheAi() {
        val none = { _: AskSpec -> false }
        assertTrue(routeQuestion("how is my finance health all time", today, found = none) is AssistantRoute.Advise)
        assertTrue(routeQuestion("what is an emergency fund", today, found = none) is AssistantRoute.Advise)
        // A search word that is in the entries stays a sum
        assertTrue(routeQuestion("uber this month", today, found = { true }) is AssistantRoute.Calculate)
    }

    @Test fun `questions about holdings go to the coach, sums of what was put in stay calculations`() {
        listOf("how are my investments doing", "is my portfolio diversified", "what is my mutual fund return", "where should I invest more").forEach {
            assertEquals(it, CoachTopic.SAVINGS, (route(it) as AssistantRoute.Advise).topic)
        }
        assertTrue(route("how much did I invest this year") is AssistantRoute.Calculate)
    }

    @Test fun `a calculated figure goes to the AI without entry descriptions`() {
        val listed = calculatedFacts(card("list my food expenses this year"))
        assertTrue(listed, listed.startsWith("CALCULATED ANSWER"))
        assertTrue(listed, listed.contains("Top entries"))
        assertFalse(listed, listed.contains("Swiggy"))
        val byCategory = calculatedFacts(card("breakdown of spending this year"))
        assertTrue(byCategory, byCategory.contains("Food & Dining"))
    }
}
