package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The Income page (income.js, spec §1.4, §2.5, §6.11). */
class IncomeTest {

    private fun inc(date: String, amount: Double, source: String = "Salary", type: String = ONLINE, bank: String = "SBI", pending: Boolean = false) =
        Income("$date-$source-$amount", amount, date, source, type, bank, "", pending)

    // Thu 22 Oct 2026; newest first, as the repository gives them
    private val today = LocalDate.of(2026, 10, 22)
    private val all = listOf(
        inc("2026-10-25", 999.0, "Future"), // future-dated: in neither total
        inc("2026-10-22", 2000.0, "Freelance", CASH, ""),
        inc("2026-10-01", 85000.0),
        inc("2026-09-30", 5000.0, "Freelance", ONLINE, "HDFC"),
        inc("2026-01-01", 1000.0, " Salary "),
        inc("2025-12-31", 7777.0),
    )

    @Test fun summary_monthAndYearToToday() {
        // month: 2,000 + 85,000; year: those + 5,000 + 1,000
        assertEquals(IncomeSummary(87000.0, 2, 93000.0), incomeSummary(all, today))
        assertEquals(IncomeSummary(0.0, 0, 0.0), incomeSummary(emptyList(), today))
    }

    @Test fun countBy_trimmed_mostUsedFirst() {
        // " Salary " counts as Salary; Cash's empty bank is skipped
        assertEquals(listOf("Salary", "Freelance", "Future"), countBy(all.map { it.source }))
        assertEquals(listOf("SBI", "HDFC"), countBy(all.map { it.bank }))
    }

    @Test fun lastFromSource_isTheNewest() {
        assertEquals("2026-10-22", lastFromSource(all, "Freelance")!!.date)
        assertNull(lastFromSource(all, "Nobody"))
    }

    @Test fun recentMeta_dayAndHowItCame() {
        assertEquals("22 Oct · Cash", recentMeta(all[1], today))
        assertEquals("1 Oct · SBI", recentMeta(all[2], today))
        assertEquals("31 Dec 2025 · SBI", recentMeta(all[5], today))
        assertEquals("22 Oct · Online · Waiting to sync", recentMeta(inc("2026-10-22", 1.0, bank = "", pending = true), today))
    }

    @Test fun addChecks_useTheAddFormsMessages() {
        fun add(amount: String = "500", date: String = "2026-10-22", source: String = "Salary", type: String = ONLINE, bank: String = "SBI", notes: String = "") =
            checkIncome(amount, date, source, type, bank, notes, adding = true)
        assertEquals(IncomeCheck.Invalid("Enter a valid amount."), add(amount = "abc"))
        assertEquals(IncomeCheck.Invalid("Select a date."), add(date = ""))
        assertEquals(IncomeCheck.Invalid("Enter income source / from."), add(source = ""))
        assertEquals(IncomeCheck.Invalid("Enter bank/wallet name for online payments."), add(bank = ""))
        // parseFloat reads "12abc" as 12; control characters are dropped; Cash has no bank
        assertEquals(
            IncomeCheck.Ok(IncomeInput(12.0, "2026-10-22", "Acme Corp", CASH, "", "paid late")),
            add(amount = "12abc", source = " Acme\u0007 Corp ", type = CASH, bank = "SBI", notes = " paid late "),
        )
        val long = add(source = "x".repeat(150), bank = "b".repeat(150)) as IncomeCheck.Ok
        assertEquals(SOURCE_MAX, long.input.source.length)
        assertEquals(BANK_MAX, long.input.bank.length)
    }
}
