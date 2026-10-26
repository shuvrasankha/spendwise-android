package com.spendwise.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Collator
import java.util.Locale

// Investments (spec §1.8, §2.9, §3.4, §6.8). Ported from invest-data.js and invest.js.
// Money put in is a 'Savings & Investments' expense with description = the holding's name and holdingId.
// Wording that carries an amount takes [whole], the page's whole-number money formatter (invest.js › moneyWhole).

data class HoldingKind(val id: String, val label: String)

/** invest-data.js › HOLDING_KINDS, in the web's order; the rules allow exactly these ids. */
val HOLDING_KINDS = listOf(
    HoldingKind("mf", "Mutual fund"),
    HoldingKind("stocks", "Stocks"),
    HoldingKind("fd", "Fixed deposit"),
    HoldingKind("rd", "Recurring deposit"),
    HoldingKind("ppf", "PPF"),
    HoldingKind("nps", "NPS"),
    HoldingKind("epf", "EPF"),
    HoldingKind("gold", "Gold"),
    HoldingKind("other", "Other"),
)

/** An unknown kind shows as Other. */
fun kindOf(id: String) = HOLDING_KINDS.firstOrNull { it.id == id } ?: HOLDING_KINDS.last()

private fun nameKey(s: String) = s.trim().lowercase()

/** holdingOf: by holdingId, else a holding named like the entry's description. Never a spending entry. */
fun holdingOf(e: Expense, holdings: List<Holding>): Holding? {
    if (isSpending(e.category)) return null
    e.holdingId?.let { id -> holdings.firstOrNull { it.id == id }?.let { return it } }
    val key = nameKey(e.description)
    return if (key.isEmpty()) null else holdings.firstOrNull { nameKey(it.name) == key }
}

data class Sip(val amount: Double, val due: String, val template: Expense)

data class PortfolioRow(
    val holding: Holding,
    val invested: Double,
    val value: Double,
    val valued: Boolean,
    val gain: Double?,
    val gainPct: Double?,
    val sip: Sip?,
    val count: Int,
)

data class Portfolio(
    val rows: List<PortfolioRow>,
    /** Entries in no holding. */
    val other: List<Expense>,
    val invested: Double,
    val value: Double,
    val gain: Double?,
    val gainPct: Double?,
    val thisMonth: Double,
    val thisYear: Double,
    /** Σ SIP amounts. */
    val monthly: Double,
) {
    val otherInvested get() = other.sumOf { it.amount }
}

private val byName = Collator.getInstance(Locale.ENGLISH) // JS localeCompare, for the tie-break

/**
 * Every holding with what was put in, what it's worth and its SIP, plus entries in no holding. Worth: the value
 * last given (on valueDate) plus what was put in after that day; with no value given, what was put in.
 */
fun portfolio(holdings: List<Holding>, expenses: List<Expense>, today: String): Portfolio {
    val month = today.take(7)
    val year = today.take(4)
    val entries = expenses.filter { !isSpending(it.category) && it.date.isNotEmpty() && it.date <= today }
    val bills = upcomingBills(expenses.filter { it.date.isNotEmpty() && it.date <= today }, today)
    val lists = holdings.associateWith { mutableListOf<Expense>() }
    val other = mutableListOf<Expense>()
    for (e in entries) {
        val h = holdingOf(e, holdings)
        if (h != null) lists.getValue(h) += e else other += e
    }
    val rows = holdings.map { h ->
        val list = lists.getValue(h)
        val invested = list.sumOf { it.amount }
        val valued = h.valued
        val value = if (valued) h.value!! + list.filter { it.date > h.valueDate!! }.sumOf { it.amount } else invested
        val gain = if (valued) value - invested else null
        // A SIP: the newest entry carrying a repeat field is the series' template
        val template = list.filter { !it.repeat.isNullOrEmpty() }.sortedByDescending { it.date }.firstOrNull()
        val bill = if (template?.repeat == "monthly") bills.firstOrNull { it.key == recurringKey(template) } else null
        PortfolioRow(
            h, invested, value, valued, gain,
            gainPct = if (gain != null && invested > 0) gain / invested * 100 else null,
            sip = bill?.let { Sip(template!!.amount, it.due, template) },
            count = list.size,
        )
    }.sortedWith { a, b -> if (a.value != b.value) b.value.compareTo(a.value) else byName.compare(a.holding.name, b.holding.name) }

    val valuedRows = rows.filter { it.valued }
    val gain = if (valuedRows.isNotEmpty()) valuedRows.sumOf { it.gain!! } else null
    val gainBase = valuedRows.sumOf { it.invested }
    return Portfolio(
        rows = rows,
        other = other,
        invested = entries.sumOf { it.amount },
        value = rows.sumOf { it.value } + other.sumOf { it.amount },
        gain = gain,
        gainPct = if (gain != null && gainBase > 0) gain / gainBase * 100 else null,
        thisMonth = entries.filter { it.date.take(7) == month }.sumOf { it.amount },
        thisYear = entries.filter { it.date.take(4) == year }.sumOf { it.amount },
        monthly = rows.sumOf { it.sip?.amount ?: 0.0 },
    )
}

/** What each goal has in linked holdings: goalId → worth. */
fun linkedToGoals(rows: List<PortfolioRow>): Map<String, Double> {
    val by = LinkedHashMap<String, Double>()
    rows.forEach { r -> r.holding.goalId?.let { by[it] = (by[it] ?: 0.0) + r.value } }
    return by
}

/** A goal with its linked holdings counted in: saved by hand plus what those holdings are worth. */
fun withLinked(goal: Goal, linked: Map<String, Double>) = goal.copy(saved = goal.saved + (linked[goal.id] ?: 0.0))

data class AfterDebts(val worth: Double, val owedToYou: Double, val youOwe: Double, val total: Double)

/** Investments plus money owed to you, less what you owe. Bank balances aren't tracked. */
fun afterDebts(worth: Double, debts: List<Debt>): AfterDebts {
    val active = debts.filter { !it.settled }
    fun total(type: String) = active.filter { it.type == type }.sumOf { debtLeft(it.amount, it.paid) }
    val owedToYou = total(THEY_OWE)
    val youOwe = total(I_OWE)
    return AfterDebts(worth, owedToYou, youOwe, worth + owedToYou - youOwe)
}

// ── Wording (invest.js › renderHero, renderHoldings, renderRecent) ──

/** "+₹ 5,000" or "−₹ 5,000". */
fun signed(n: Double, whole: (Double) -> String) = (if (n < 0) "−" else "+") + whole(Math.abs(n))

private fun toFixed1(n: Double) = BigDecimal(n).setScale(1, RoundingMode.HALF_UP).toPlainString()

/** Nothing put in and no holdings: the hero asks for the first one. */
fun Portfolio.isEmpty(holdings: List<Holding>) = invested == 0.0 && holdings.isEmpty()

fun Portfolio.worthSub(whole: (Double) -> String): String {
    if (gain == null) return "${whole(invested)} put in"
    val pct = gainPct?.let { " (${if (it >= 0) "+" else "−"}${toFixed1(Math.abs(it))}%)" } ?: ""
    return "${whole(invested)} put in · ${signed(gain, whole)}$pct ${if (gain >= 0) "gain" else "loss"}"
}

/** [monthName] is the current month, "October". */
fun Portfolio.monthSub(monthName: String, whole: (Double) -> String) =
    listOf("${whole(thisMonth)} put in this $monthName", if (monthly != 0.0) "${whole(monthly)} a month in SIPs" else "")
        .filter { it.isNotEmpty() }.joinToString(" · ")

/** The After debts line shows only when there are open debts either way. */
fun AfterDebts.shown() = owedToYou > 0 || youOwe > 0
fun AfterDebts.totalText(whole: (Double) -> String) = if (total < 0) "−${whole(-total)}" else whole(total)
fun AfterDebts.note(whole: (Double) -> String) = listOf(
    "Investments",
    if (owedToYou != 0.0) "plus ${whole(owedToYou)} owed to you" else "",
    if (youOwe != 0.0) "less ${whole(youOwe)} you owe" else "",
).filter { it.isNotEmpty() }.joinToString(", ") + ". Bank balances aren't counted."

/** "Mutual fund · ₹ 5,000 a month · for House". A holding named like its kind says it once. */
fun PortfolioRow.sub(goals: List<Goal>, whole: (Double) -> String): String {
    val kind = kindOf(holding.kind)
    val goal = holding.goalId?.let { id -> goals.firstOrNull { it.id == id } }
    return listOf(
        if (kind.label.lowercase() == holding.name.trim().lowercase()) "" else kind.label,
        sip?.let { "${whole(it.amount)} a month" } ?: "",
        goal?.let { "for ${it.name}" } ?: "",
    ).filter { it.isNotEmpty() }.joinToString(" · ")
}

fun PortfolioRow.gainText(whole: (Double) -> String) = when {
    gain == null -> if (count > 0) "${whole(invested)} put in" else "Nothing put in yet"
    else -> "${signed(gain, whole)} ${if (gain >= 0) "gain" else "loss"}"
}

/** A recent entry's title: its holding, else its description, else "Investment". */
fun entryTitle(e: Expense, holdings: List<Holding>) =
    holdingOf(e, holdings)?.name ?: if (e.description.isNotEmpty() && e.description != "-") e.description else "Investment"

fun entryMeta(e: Expense, holdings: List<Holding>, today: java.time.LocalDate) = listOf(
    shortDay(e.date, today),
    if (holdingOf(e, holdings) == null) "Not in a holding" else "",
    if (e.repeat == "monthly") "Every month" else "",
    if (e.pending) "Waiting to sync" else "",
).filter { it.isNotEmpty() }.joinToString(" · ")

const val INVEST_RECENT_COUNT = 10

// ── Checks (invest.js › addEntry, saveHolding, saveEntry) ──

/** invest.js › num: digits and dots only, a number ≥ 0, to 2 places; else null. */
fun investNum(v: String): Double? {
    val n = jsParseFloat(v.replace(Regex("[^\\d.]"), ""))
    return if (n >= 0 && n.isFinite()) round2(n) else null
}

/** The "Paid with" choices: label to stored value. "Bank or auto-debit" first, so it's the default. */
val INVEST_PAYMENTS = listOf("Net Banking" to "Bank or auto-debit", "UPI" to "UPI", "Debit Card" to "Debit card", "Cash" to "Cash", "Other" to "Other")

data class InvestEntryInput(
    val amount: Double, val date: String, val payment: String, val notes: String,
    val holding: Holding,
    /** "monthly", "stopped" (switched off on a SIP) or null. */
    val repeat: String?,
)

sealed interface Checked<out T> {
    data class Ok<T>(val value: T) : Checked<T>
    data class Invalid(val message: String) : Checked<Nothing>
}

/** addEntry. An empty amount uses the placeholder: the SIP amount when there is one. */
fun checkInvestEntry(
    holding: Holding?, amount: String, placeholder: String, date: String, payment: String, notes: String,
    repeat: Boolean, wasSip: Boolean,
): Checked<InvestEntryInput> {
    val n = jsParseFloat(amount.ifEmpty { placeholder })
    if (holding == null) return Checked.Invalid("Pick a holding first, or add a new one.")
    if (n.isNaN() || n <= 0) return Checked.Invalid("Enter the amount you put in.")
    if (date.isEmpty()) return Checked.Invalid("Pick the date.")
    val r = if (repeat) "monthly" else if (wasSip) "stopped" else null
    return Checked.Ok(InvestEntryInput(n, date, payment, sanitize(notes.trim(), 500), holding, r))
}

/** What a holding save writes: [value] and [valueDate] together or neither, [goalId] or none. */
data class HoldingFields(val name: String, val kind: String, val value: Double?, val valueDate: String?, val goalId: String?)

/**
 * saveHolding's checks and fields. [editing] is the holding being edited, or null for a new one. A changed
 * value is dated [today]; an unchanged one keeps its date, so later payments still add to it.
 */
fun checkHolding(
    name: String, kind: String, value: String, goalId: String,
    editing: Holding?, holdings: List<Holding>, today: String,
): Checked<HoldingFields> {
    val clean = sanitize(name.trim(), 60)
    val raw = value.trim()
    val n = if (raw.isNotEmpty()) investNum(raw) else null
    if (clean.isEmpty()) return Checked.Invalid("Give it a name, like the fund or the bank.")
    if (holdings.any { it.id != editing?.id && it.name.trim().lowercase() == clean.lowercase() }) {
        return Checked.Invalid("You already have a holding with that name.")
    }
    if (raw.isNotEmpty() && n == null) return Checked.Invalid("Enter what it is worth as a number, or leave it empty.")
    val changed = n != editing?.value
    return Checked.Ok(
        HoldingFields(clean, kind, n, if (n == null) null else if (changed) today else editing?.valueDate, goalId.ifEmpty { null }),
    )
}

/** saveEntry: [holding] is where it goes, or null for Not in a holding. */
data class EntryEdit(val amount: Double, val date: String, val holding: Holding?)

private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")

fun checkEntryEdit(amount: String, date: String, holding: Holding?): Checked<EntryEdit> {
    val n = investNum(amount)
    if (n == null || n == 0.0) return Checked.Invalid("Enter the amount as a number.")
    if (!isoDate.matches(date)) return Checked.Invalid("Pick the date.")
    return Checked.Ok(EntryEdit(n, date, holding))
}
