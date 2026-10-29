package com.spendwise.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Income entries: the add form and edit dialogs (income.js, dashboard.js › saveIncEdit; spec §1.4, §2.5, §6.11).

const val ONLINE = "Online"
const val CASH = "Cash"
val INCOME_PAYMENT_TYPES = listOf(ONLINE, CASH)

/** Field limits the rules enforce (spec §2.5); the form stops typing past them. */
const val SOURCE_MAX = 100
const val BANK_MAX = 100
const val INCOME_NOTES_MAX = 500

/** The fields an income edit writes, checked and cleaned. [bank] is "" for Cash. */
data class IncomeInput(
    val amount: Double,
    val date: String,
    val source: String,
    val paymentType: String,
    val bank: String,
    val notes: String,
)

sealed interface IncomeCheck {
    data class Ok(val input: IncomeInput) : IncomeCheck
    data class Invalid(val message: String) : IncomeCheck
}

/**
 * income.js › addIncome (adding) and saveEditIncome / dashboard.js › saveIncEdit (editing): the same checks in the
 * same order, but the add form's messages are longer. Text is sanitized as income.js does (control characters out,
 * limits applied); History's saveIncEdit skips that, which only differs for pasted control characters.
 */
fun checkIncome(
    amount: String, date: String, source: String, paymentType: String, bank: String, notes: String, adding: Boolean,
): IncomeCheck {
    val value = jsParseFloat(amount)
    val src = sanitize(source.trim(), SOURCE_MAX)
    val bankName = sanitize(if (paymentType == ONLINE) bank.trim() else "", BANK_MAX)
    return when {
        !(value > 0) -> IncomeCheck.Invalid("Enter a valid amount.")
        date.isEmpty() -> IncomeCheck.Invalid("Select a date.")
        src.isEmpty() -> IncomeCheck.Invalid(if (adding) "Enter income source / from." else "Enter income source.")
        paymentType == ONLINE && bankName.isEmpty() ->
            IncomeCheck.Invalid(if (adding) "Enter bank/wallet name for online payments." else "Enter bank/wallet name.")
        else -> IncomeCheck.Ok(IncomeInput(value, date, src, paymentType, bankName, sanitize(notes.trim(), INCOME_NOTES_MAX)))
    }
}

// ── The Income page (income.js › updateIncomeSummaryCards, spec §6.11) ──

/** 5 here; the web shows 8. The rest are one tap away under Show more. */
const val RECENT_COUNT = 5

data class IncomeSummary(val monthTotal: Double, val monthCount: Int, val yearTotal: Double)

/** This month and this year, from the 1st (or Jan 1) to today; future-dated entries don't count. */
fun incomeSummary(income: List<Income>, today: LocalDate): IncomeSummary {
    val t = today.toString()
    val ms = today.withDayOfMonth(1).toString()
    val ys = today.withDayOfYear(1).toString()
    val month = income.filter { it.date >= ms && it.date <= t }
    return IncomeSummary(month.sumOf { it.amount }, month.size, income.filter { it.date >= ys && it.date <= t }.sumOf { it.amount })
}

/** countBy: the trimmed, non-empty values, most used first, ties in first-seen order. */
fun countBy(values: List<String>): List<String> {
    val counts = LinkedHashMap<String, Int>()
    values.map { it.trim() }.filter { it.isNotEmpty() }.forEach { counts[it] = (counts[it] ?: 0) + 1 }
    return counts.entries.sortedByDescending { it.value }.map { it.key }
}

/** Tapping a source chip: the newest entry from that source, whose payment type and bank are likely again. */
fun lastFromSource(income: List<Income>, source: String): Income? = income.firstOrNull { it.source == source }

/** A recent row's second line: the day, Cash or the bank (Online without one), and "Waiting to sync". */
fun recentMeta(i: Income, today: LocalDate): String =
    listOf(shortDay(i.date, today), if (i.paymentType == CASH) CASH else i.bank.ifEmpty { ONLINE }, if (i.pending) "Waiting to sync" else "")
        .filter { it.isNotEmpty() }.joinToString(" · ")

private val shortThisYear = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("en-IN"))
private val shortOtherYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("en-IN"))

/** helpers.js › shortDay: "26 Oct", or "30 Dec 2025" outside this year (en-IN). */
fun shortDay(ds: String, today: LocalDate): String {
    val d = runCatching { LocalDate.parse(ds) }.getOrNull() ?: return ds
    return d.format(if (d.year == today.year) shortThisYear else shortOtherYear)
}
