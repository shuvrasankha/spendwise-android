package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** tests/history.test.mjs, ported, plus the income tab and the Android-only pieces. */
class HistoryTest {

    private val now = LocalDate.of(2026, 10, 22)

    private fun e(id: String, date: String, amount: Double, category: String, description: String, payment: String = "", cardName: String = "", notes: String = "", tags: List<String> = emptyList()) =
        Entry.Spent(Expense(id, amount, category, date, payment, cardName, description, notes, tags))

    private val expenses = listOf(
        e("a", "2026-10-22", 240.0, FOOD, "Lunch", "UPI", tags = listOf("work")),
        e("b", "2026-10-22", 180.0, "Transport", "Uber to office", "Card", "HDFC"),
        e("c", "2026-10-21", 1299.0, "Shopping", "Shoes", "Card", "ICICI", "Gift for my brother"),
        e("d", "2026-09-12", 3200.0, "Groceries", "Monthly groceries", "UPI"),
        e("f", "2025-12-30", 450.0, FOOD, "New year dinner", "Cash"),
    )

    private fun ids(
        from: String = "", to: String = "", kinds: Set<String> = emptySet(), q: String = "", sort: HistorySort = HistorySort.NEWEST,
    ) = filterHistory(expenses, from, to, kinds, q, sort).joinToString("") { it.id }

    @Test fun ranges() {
        assertEquals("2026-10-01" to "2026-10-22", historyRange(HistoryRange.MONTH, now))
        assertEquals("2026-09-01" to "2026-09-30", historyRange(HistoryRange.LAST_MONTH, now))
        assertEquals("2026-08-01" to "2026-10-22", historyRange(HistoryRange.QUARTER, now))
        assertEquals("2026-01-01" to "2026-10-22", historyRange(HistoryRange.YEAR, now))
        assertEquals("2025-12-01" to "2025-12-31", historyRange(HistoryRange.CUSTOM, now, "2025-12-01", "2025-12-31"))
        assertEquals("" to "", historyRange(HistoryRange.ALL, now))
        val jan = LocalDate.of(2026, 1, 15)
        assertEquals("2025-12-01" to "2025-12-31", historyRange(HistoryRange.LAST_MONTH, jan))
        assertEquals("2025-11-01" to "2026-01-15", historyRange(HistoryRange.QUARTER, jan))
    }

    @Test fun exportRange_swapsBackwardsCustomDates() {
        assertEquals("2026-10-01" to "2026-10-20", exportRange(HistoryRange.CUSTOM, now, "2026-10-20", "2026-10-01"))
        assertEquals("" to "2026-10-01", exportRange(HistoryRange.CUSTOM, now, "", "2026-10-01"))
    }

    @Test fun sorts_stable() {
        assertEquals("abcdf", ids()) // a and b share a date and keep their order
        assertEquals('f', ids(sort = HistorySort.OLDEST)[0])
        assertEquals("dcfab", ids(sort = HistorySort.LARGEST))
    }

    @Test fun datesInclusive_chips() {
        assertEquals("abc", ids(from = "2026-10-21", to = "2026-10-22"))
        assertEquals("f", ids(from = "2025-12-01", to = "2025-12-31"))
        assertEquals("b", ids(kinds = setOf("Transport")))
        assertEquals("bc", ids(kinds = setOf("Transport", "Shopping")))
    }

    @Test fun search_everyWordInSomeField() {
        assertEquals("b", ids(q = "UBER"))
        assertEquals("c", ids(q = "brother"))
        assertEquals("c", ids(q = "icici"))
        assertEquals("a", ids(q = "work"))
        assertEquals("f", ids(q = "cash"))
        assertEquals("c", ids(q = "icici card"))
        assertEquals("", ids(q = "icici upi"))
        assertEquals(ids(), ids(q = "  "))
        assertEquals("", ids(q = "nothing like this"))
        assertEquals("a", ids(from = "2026-10-01", kinds = setOf(FOOD), q = "lunch"))
    }

    @Test fun dayHeadings() {
        assertEquals("Today", historyDay("2026-10-22", now))
        assertEquals("Yesterday", historyDay("2026-10-21", now))
        assertEquals("Mon, 19 Oct", historyDay("2026-10-19", now))
        assertEquals("Tue, 30 Dec, 2025", historyDay("2025-12-30", now))
    }

    private val trip = listOf(
        e("h", "2026-10-02", 6000.0, "Travel", "Goa hotel", tags = listOf("Goa2026")),
        e("f", "2026-10-03", 900.0, FOOD, "Beach shack", tags = listOf("goa2026", "friends")),
        e("g", "2026-10-05", 300.0, FOOD, "Goa sweets for office"),
    )

    @Test fun tagSearch_exact_anyCase() {
        fun run(q: String) = filterHistory(trip, q = q).map { it.id }.sorted().joinToString("")
        assertEquals("fh", run("#goa2026"))
        assertEquals("", run("#goa"))
        assertEquals("fgh", run("goa"))
        assertEquals("goa2026", searchTag(" #Goa2026 "))
        assertNull(searchTag("#goa trip"))
        assertEquals(listOf("Travel" to 6000.0, FOOD to 1200.0), totalsBy(trip))
        assertEquals(listOf("Goa2026", "friends"), tagsInUse(trip))
    }

    private fun inc(id: String, source: String, paymentType: String = "Online", bank: String = "SBI", notes: String = "") =
        Entry.Earned(Income(id, 1000.0, "2026-10-01", source, paymentType, bank, notes))

    @Test fun incomeTab_fieldsAndNoTags() {
        val income = listOf(inc("s", "Salary"), inc("x", "", "Cash", "", "#bonus"))
        assertEquals(listOf("Other", "Salary").sorted(), income.map { it.kind }.sorted())
        assertEquals("Income", income[1].title)
        assertEquals(listOf("s"), filterHistory(income, q = "sbi").map { it.id })
        // Income has no tags, so "#bonus" is a plain search and finds the note
        assertEquals(listOf("x"), filterHistory(income, q = "#bonus").map { it.id })
    }

    @Test fun kindChips_mostUsedFirst() {
        assertEquals(listOf(FOOD, "Transport", "Shopping", "Groceries"), kindsInUse(expenses))
    }

    @Test fun rowMeta() {
        assertEquals(listOf("Transport", "Card (HDFC)"), expenses[1].meta())
        assertEquals(listOf(FOOD, "UPI", "#work"), expenses[0].meta())
        val emi = Entry.Spent(ex(5000.0, "Shopping", "2026-10-10", "Phone", repeat = "monthly", emiTotal = 6).copy(pending = true))
        assertEquals(listOf("Shopping", "UPI", "Waiting to sync", "EMI · 6 payments"), emi.meta())
        assertEquals(listOf("Online", "SBI"), inc("s", "Salary").meta())
    }

    // ── CSV (tests/csv-export.test.mjs) ──

    private val lunch = e(
        "l", "2026-10-22", 240.0, FOOD, "Lunch, \"the usual\"", "Card", "HDFC", "with team\nsplit later", listOf("work", "goa2026"),
    )
    private val salary = Entry.Earned(Income("s", 85000.0, "2026-10-01", "Salary", "Bank transfer", "SBI", ""))

    @Test fun csvCell_quotesOnlyWhenNeeded_andDefusesFormulas() {
        assertEquals("Groceries", csvCell("Groceries"))
        assertEquals("\"Lunch, \"\"the usual\"\"\"", csvCell("Lunch, \"the usual\""))
        assertEquals("\"line one\nline two\"", csvCell("line one\nline two"))
        assertEquals("", csvCell(""))
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\",\"\"click\"\")\"", csvCell("=HYPERLINK(\"http://x\",\"click\")"))
        listOf("+1", "-2", "@SUM(A1)", "\tx").forEach { assertEquals("'$it", csvCell(it)) }
    }

    /** The whole file, byte for byte, as the web's buildCsv writes it for the same two entries. */
    @Test fun buildCsv_matchesTheWebsFile() {
        val expected = "﻿" +
            "Date,Type,Category or source,Description,Payment,Card or bank,Tags,Notes,Amount (INR)\r\n" +
            "2026-10-22,Expense,Food & Dining,\"Lunch, \"\"the usual\"\"\",Card,HDFC,#work #goa2026,\"with team\nsplit later\",240.00\r\n" +
            "2026-10-01,Income,Salary,,Bank transfer,SBI,,,85000.00\r\n" +
            "\r\n" +
            "Total spent,,,,,,,,240.00\r\n" +
            "Total income,,,,,,,,85000.00\r\n" +
            "Net,,,,,,,,84760.00\r\n"
        assertEquals(expected, buildCsv(listOf(lunch, salary), "INR", total = true))
        assertEquals(expected.substringBefore("\r\n\r\n") + "\r\n", buildCsv(listOf(lunch, salary), "INR", total = false))
    }

    @Test fun buildCsv_dashDescription_currency_andTotals() {
        val plain = Entry.Spent(lunch.expense.copy(description = "-", notes = "", tags = emptyList()))
        val csv = buildCsv(listOf(plain), "USD", total = true)
        assertTrue(csv.contains("Amount (USD)"))
        assertTrue(csv.contains("2026-10-22,Expense,Food & Dining,,Card,HDFC,,,240.00\r\n"))
        assertTrue(csv.endsWith("\r\n\r\nTotal spent,,,,,,,,240.00\r\n"))
        assertFalse(csv.contains("Net"))
        assertFalse(buildCsv(emptyList(), "INR", total = true).contains("Total"))
    }

    @Test fun toFixed2_likeJs() {
        assertEquals("1.00", toFixed2(1.005)) // 1.00499999… in binary
        assertEquals("1.13", toFixed2(1.125)) // exact tie rounds up
        assertEquals("182.50", toFixed2(182.5))
        assertEquals("-12.50", toFixed2(-12.5))
        assertEquals("0.00", toFixed2(-0.0))
    }

    @Test fun fileNames() {
        assertEquals("SpendWise_Expenses_2026-10-01_to_2026-10-24.csv", exportFileName("Expenses", "2026-10-01", "2026-10-24", "2026-10-24"))
        assertEquals("SpendWise_Income_all-time_2026-10-24.csv", exportFileName("Income", "", "", "2026-10-24"))
        assertEquals("SpendWise_Expenses-and-income_start_to_2026-09-30.csv", exportFileName("Expenses-and-income", "", "2026-09-30", "2026-10-24"))
    }

    @Test fun exportBoth_ignoresFilters_newestFirst() {
        val list = exportBoth(listOf(lunch.expense, expenses[3].expense), listOf(salary.income), "2026-10-01", "")
        assertEquals(listOf("l", "s"), list.map { it.id })
    }

    @Test fun incomeEditChecks_inWebOrder() {
        fun check(amount: String = "500", date: String = "2026-10-01", source: String = "Salary", type: String = ONLINE, bank: String = "SBI") =
            checkIncomeEdit(amount, date, source, type, bank, " ")
        assertEquals(IncomeCheck.Invalid("Enter a valid amount."), check(amount = "0"))
        assertEquals(IncomeCheck.Invalid("Enter a valid amount."), check(amount = ""))
        assertEquals(IncomeCheck.Invalid("Select a date."), check(date = ""))
        assertEquals(IncomeCheck.Invalid("Enter income source."), check(source = "  "))
        assertEquals(IncomeCheck.Invalid("Enter bank/wallet name."), check(bank = " "))
        assertEquals(IncomeCheck.Ok(IncomeInput(500.0, "2026-10-01", "Salary", CASH, "", "")), check(type = CASH, bank = "SBI"))
    }
}
