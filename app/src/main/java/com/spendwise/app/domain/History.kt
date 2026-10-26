package com.spendwise.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// History (spec §1.6, §2.6, §6.10). Ported from history-data.js, csv-export.js and dashboard.js › HISTORY_TABS.
// Dates are local "YYYY-MM-DD" strings, compared as text.

/** One History row: an expense or an income entry, with the fields each tab reads (dashboard.js › HISTORY_TABS). */
sealed interface Entry {
    val id: String
    val date: String
    val amount: Double
    val notes: String
    val pending: Boolean

    /** The chip it falls under: a category, or an income source ("Other" without one). */
    val kind: String
    val title: String
    /** The searchable fields. */
    val text: List<String>
    val tags: List<String>

    data class Spent(val expense: Expense) : Entry {
        override val id get() = expense.id
        override val date get() = expense.date
        override val amount get() = expense.amount
        override val notes get() = expense.notes
        override val pending get() = expense.pending
        override val kind get() = expense.category
        override val title get() = expense.description.ifEmpty { expense.category }
        override val text get() = listOf(expense.description, expense.category, expense.notes, expense.payment, expense.cardName) + expense.tags
        override val tags get() = expense.tags
    }

    data class Earned(val income: Income) : Entry {
        override val id get() = income.id
        override val date get() = income.date
        override val amount get() = income.amount
        override val notes get() = income.notes
        override val pending get() = income.pending
        override val kind get() = income.source.ifEmpty { "Other" }
        override val title get() = income.source.ifEmpty { "Income" }
        override val text get() = listOf(income.source, income.bank, income.notes, income.paymentType)
        override val tags get() = emptyList<String>()
    }
}

/** The row's second line: category, payment (with card), tags; then whether it waits to sync or repeats. */
fun Entry.meta(): List<String> = when (this) {
    is Entry.Spent -> listOf(
        expense.category,
        if (expense.cardName.isNotEmpty()) "${expense.payment} (${expense.cardName})" else expense.payment,
    ) + expense.tags.map { "#$it" }
    is Entry.Earned -> listOf(income.paymentType, income.bank)
}.filter { it.isNotEmpty() } + listOfNotNull(
    "Waiting to sync".takeIf { pending },
    (this as? Entry.Spent)?.expense?.takeIf { it.repeat == "monthly" }?.let { e ->
        if ((e.emiTotal ?: 0) > 0) "EMI · ${e.emiTotal} payments" else "Repeats monthly"
    },
)

/** The "Dates" choices, keyed as the web's select values. */
enum class HistoryRange(val label: String) {
    ALL("All time"), MONTH("This month"), LAST_MONTH("Last month"), QUARTER("Last 3 months"), YEAR("This year"), CUSTOM("Custom dates")
}

/** historyRange: [from, to], "" for open-ended. [customFrom]/[customTo] are the date inputs, used for CUSTOM. */
fun historyRange(range: HistoryRange, today: LocalDate, customFrom: String = "", customTo: String = ""): Pair<String, String> {
    val first = today.withDayOfMonth(1)
    return when (range) {
        HistoryRange.MONTH -> first.toString() to today.toString()
        HistoryRange.LAST_MONTH -> first.minusMonths(1).toString() to first.minusDays(1).toString()
        HistoryRange.QUARTER -> first.minusMonths(2).toString() to today.toString()
        HistoryRange.YEAR -> today.withDayOfYear(1).toString() to today.toString()
        HistoryRange.CUSTOM -> customFrom to customTo
        HistoryRange.ALL -> "" to ""
    }
}

private val tagSearch = Regex("""^#\S+$""")

/** The tag a search names, lowercase without "#", or null for a plain search. */
fun searchTag(q: String): String? = q.trim().takeIf { tagSearch.matches(it) }?.drop(1)?.lowercase()

enum class HistorySort(val label: String) { NEWEST("Newest first"), OLDEST("Oldest first"), LARGEST("Largest first") }

/**
 * filterHistory: rows within the dates, in the chosen kinds, matching the search, sorted (stably, like JS).
 * A "#tag" search matches that tag exactly, on expenses only (income has no tags, so there it's plain words).
 * Otherwise every word must appear in some field.
 */
fun filterHistory(
    rows: List<Entry>,
    from: String = "",
    to: String = "",
    kinds: Set<String> = emptySet(),
    q: String = "",
    sort: HistorySort = HistorySort.NEWEST,
): List<Entry> {
    val query = q.lowercase().trim()
    val words = query.split(Regex("""\s+""")).filter { it.isNotEmpty() }
    fun matches(r: Entry): Boolean {
        val tag = if (r is Entry.Spent) searchTag(q) else null
        if (tag != null) return r.tags.any { it.lowercase() == tag }
        val hay = r.text.filter { it.isNotEmpty() }.joinToString("\u0001") { it.lowercase() }
        return words.all { hay.contains(it) }
    }
    val out = rows.filter { r ->
        (from.isEmpty() || r.date >= from) && (to.isEmpty() || r.date <= to) &&
            (kinds.isEmpty() || r.kind in kinds) &&
            (query.isEmpty() || matches(r))
    }
    return when (sort) {
        HistorySort.LARGEST -> out.sortedByDescending { it.amount }
        HistorySort.OLDEST -> out.sortedBy { it.date }
        HistorySort.NEWEST -> out.sortedByDescending { it.date }
    }
}

/** The kind chips: every kind in the data, most used first, ties in first-seen order (renderHistory). */
// shortcut: a JS object lists integer-like keys ("2024") first; a source named like that may order differently, rare
fun kindsInUse(rows: List<Entry>): List<String> {
    val counts = LinkedHashMap<String, Int>()
    rows.forEach { counts[it.kind] = (counts[it.kind] ?: 0) + 1 }
    return counts.entries.sortedByDescending { it.value }.map { it.key }
}

/** totalsBy: [kind, total], largest first. Where a trip's money went. */
fun totalsBy(rows: List<Entry>): List<Pair<String, Double>> {
    val totals = LinkedHashMap<String, Double>()
    rows.forEach { totals[it.kind] = (totals[it.kind] ?: 0.0) + it.amount }
    return totals.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

/** tagsInUse: most used first, as written the first time each was seen. */
fun tagsInUse(rows: List<Entry>): List<String> {
    val seen = LinkedHashMap<String, Pair<String, Int>>()
    rows.forEach { r ->
        r.tags.forEach { t ->
            val k = t.lowercase()
            seen[k] = (seen[k]?.first ?: t) to ((seen[k]?.second ?: 0) + 1)
        }
    }
    return seen.values.sortedByDescending { it.second }.map { it.first }
}

private val enIN = Locale.forLanguageTag("en-IN")
private val dayThisYear = DateTimeFormatter.ofPattern("EEE, d MMM", enIN)
private val dayOtherYear = DateTimeFormatter.ofPattern("EEE, d MMM, yyyy", enIN)

/** historyDay: Today, Yesterday, "Mon, 19 Oct", or "Tue, 30 Dec, 2025" outside this year (en-IN, as the web). */
fun historyDay(ds: String, today: LocalDate): String {
    if (ds == today.toString()) return "Today"
    if (ds == today.minusDays(1).toString()) return "Yesterday"
    val d = runCatching { LocalDate.parse(ds) }.getOrNull() ?: return ds
    return d.format(if (d.year == today.year) dayThisYear else dayOtherYear)
}

// ── CSV export (csv-export.js) ──

private val formula = Regex("^[=+\\-@\t\r]")
private val needsQuotes = Regex("[\",\r\n]")

/** One CSV field: a formula-looking value gets a leading ', and it's quoted only when it has to be. */
fun csvCell(value: String): String {
    val s = if (formula.containsMatchIn(value)) "'$value" else value
    return if (needsQuotes.containsMatchIn(s)) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

/** JS `n.toFixed(2)`: the exact binary value rounded half up. */
fun toFixed2(n: Double): String = BigDecimal(n).setScale(2, RoundingMode.HALF_UP).toPlainString()

private fun text(v: String) = if (v.isNotEmpty() && v != "-") v else ""

private val HEADERS = listOf("Date", "Type", "Category or source", "Description", "Payment", "Card or bank", "Tags", "Notes")

private fun cells(e: Entry): List<String> = when (e) {
    is Entry.Spent -> e.expense.let { x ->
        listOf(x.date, "Expense", x.category, text(x.description), x.payment, x.cardName, x.tags.joinToString(" ") { "#$it" }, text(x.notes))
    }
    is Entry.Earned -> e.income.let { x -> listOf(x.date, "Income", x.source, "", x.paymentType, x.bank, "", text(x.notes)) }
}

/**
 * buildCsv: UTF-8 with a byte-order mark, CRLF lines. [total] adds a line per kind present (and Net when
 * both are) under a blank line, so a sort or filter in the sheet skips them.
 */
fun buildCsv(entries: List<Entry>, currencyCode: String, total: Boolean): String {
    val lines = mutableListOf((HEADERS + "Amount ($currencyCode)").joinToString(",") { csvCell(it) })
    entries.forEach { e -> lines += (cells(e).map(::csvCell) + toFixed2(e.amount)).joinToString(",") }
    if (total && entries.isNotEmpty()) {
        fun line(label: String, amount: Double) = (listOf(label) + List(HEADERS.size - 1) { "" } + toFixed2(amount)).joinToString(",")
        val spent = entries.filterIsInstance<Entry.Spent>().sumOf { it.amount }
        val earned = entries.filterIsInstance<Entry.Earned>().sumOf { it.amount }
        val hasSpent = entries.any { it is Entry.Spent }
        val hasEarned = entries.any { it is Entry.Earned }
        lines += ""
        if (hasSpent) lines += line("Total spent", spent)
        if (hasEarned) lines += line("Total income", earned)
        if (hasSpent && hasEarned) lines += line("Net", earned - spent)
    }
    return "﻿" + lines.joinToString("\r\n") + "\r\n"
}

/** "SpendWise_Expenses_2026-10-01_to_2026-10-24.csv", or "…_all-time_<today>.csv" with no dates. */
fun exportFileName(what: String, from: String, to: String, today: String): String {
    val span = if (from.isNotEmpty() || to.isNotEmpty()) "${from.ifEmpty { "start" }}_to_${to.ifEmpty { today }}" else "all-time_$today"
    return "SpendWise_${what}_$span.csv"
}

/** exportDates: a custom range given backwards is swapped. */
fun exportRange(range: HistoryRange, today: LocalDate, customFrom: String, customTo: String): Pair<String, String> {
    val (from, to) = historyRange(range, today, customFrom, customTo)
    return if (from.isNotEmpty() && to.isNotEmpty() && from > to) to to from else from to to
}

/** "Expenses and income": both, in the dates, ignoring search and chips, newest first. */
fun exportBoth(expenses: List<Expense>, income: List<Income>, from: String, to: String): List<Entry> =
    (expenses.map { Entry.Spent(it) } + income.map { Entry.Earned(it) })
        .filter { (from.isEmpty() || it.date >= from) && (to.isEmpty() || it.date <= to) }
        .sortedByDescending { it.date }
