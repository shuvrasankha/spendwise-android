package com.spendwise.app.domain

import java.time.LocalDate

// The Debts page (spec §1.5, §2.7, §6.7). Ported from debt.js, debt-data.js, split.js and helpers.js › peopleFrom.
// Wording that carries an amount takes [fmt], the page's money formatter, so this stays free of Android.

fun Debt.left(): Double = debtLeft(amount, paid)
fun Debt.claim(): Claim? = debtClaim(amount, paid, settled, claimDate, claimAmount)

/** updateDebtSummary, over open debts. [people] counts distinct names as typed. */
data class DebtSummary(
    val owedToYou: Double,
    val youOwe: Double,
    val net: Double,
    val open: Int,
    val people: Int,
    val owedCount: Int,
    val oweCount: Int,
) {
    val netWord get() = if (net > 0) "in your favour" else if (net < 0) "you owe overall" else "all square"
    val sub get() = if (open > 0) "${count(open, "open debt", "open debts")} with ${count(people, "person", "people")}" else "No open debts"
}

private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

fun debtSummary(debts: List<Debt>): DebtSummary {
    val active = debts.filter { !it.settled }
    val owed = active.filter { it.type == THEY_OWE }
    val owe = active.filter { it.type == I_OWE }
    val owedToYou = owed.sumOf { it.left() }
    val youOwe = owe.sumOf { it.left() }
    return DebtSummary(owedToYou, youOwe, owedToYou - youOwe, active.size, active.map { it.person }.toSet().size, owed.size, owe.size)
}

// ── By person (debt-data.js) ──

data class DebtPerson(val key: String, val name: String, val email: String, val owedToYou: Double, val youOwe: Double, val net: Double, val count: Int)

private fun emailOf(d: Debt) = d.email.trim().lowercase()
private fun nameKey(d: Debt) = d.person.trim().lowercase()

/** Open debts per person, biggest balance first. A person is their email when one is known, else their name. */
fun debtsByPerson(debts: List<Debt>): List<DebtPerson> {
    val open = debts.filter { !it.settled && it.person.isNotBlank() }
    val emailsByName = LinkedHashMap<String, MutableSet<String>>()
    open.forEach { d -> emailOf(d).takeIf { it.isNotEmpty() }?.let { emailsByName.getOrPut(nameKey(d)) { linkedSetOf() } += it } }
    val people = LinkedHashMap<String, DebtPerson>()
    for (d in open.sortedByDescending { it.date }) {
        val known = emailsByName[nameKey(d)]
        val email = emailOf(d).ifEmpty { if (known?.size == 1) known.first() else "" }
        val key = email.ifEmpty { "name:${nameKey(d)}" }
        val p = people[key] ?: DebtPerson(key, d.person.trim(), email, 0.0, 0.0, 0.0, 0)
        val left = d.left()
        people[key] = if (d.type == I_OWE) p.copy(youOwe = p.youOwe + left, count = p.count + 1)
        else p.copy(owedToYou = p.owedToYou + left, count = p.count + 1)
    }
    return people.values
        .map { it.copy(owedToYou = round2(it.owedToYou), youOwe = round2(it.youOwe), net = round2(it.owedToYou - it.youOwe)) }
        .sortedWith(compareByDescending<DebtPerson> { Math.abs(it.net) }.thenByDescending { it.count })
}

fun isPersons(d: Debt, person: DebtPerson): Boolean {
    val e = emailOf(d)
    val sameName = e.isEmpty() && nameKey(d) == person.name.lowercase()
    return if (person.email.isNotEmpty()) e == person.email || sameName else sameName
}

fun DebtPerson.balance(fmt: (Double) -> String) = if (net > 0) "Owes you ${fmt(net)}" else if (net < 0) "You owe ${fmt(-net)}" else "All square"

fun DebtPerson.sub(fmt: (Double) -> String): String {
    val both = if (owedToYou > 0 && youOwe > 0) " · ${fmt(owedToYou)} to you, ${fmt(youOwe)} from you" else ""
    return listOf("$count open", email).filter { it.isNotEmpty() }.joinToString(" · ") + both
}

// ── Known people (helpers.js › peopleFrom) ──

data class KnownPerson(val name: String, val email: String)

/** Everyone in your debts, newest first, one per name and email. A bare name goes when that name has an email. */
fun peopleFrom(debts: List<Debt>): List<KnownPerson> {
    val byKey = LinkedHashMap<String, KnownPerson>()
    for (d in debts.sortedByDescending { it.date }) {
        val name = d.person.trim()
        if (name.isEmpty()) continue
        byKey.putIfAbsent("${name.lowercase()}|${d.email}", KnownPerson(name, d.email))
    }
    val named = byKey.values.filter { it.email.isNotEmpty() }.map { it.name.lowercase() }.toSet()
    return byKey.values.filter { it.email.isNotEmpty() || it.name.lowercase() !in named }
}

// ── Adding (debt.js › addDebt, split.js › emailError) ──

private val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

/** [email] is already trimmed and lowercase; "" means not shared. */
fun emailError(email: String, myEmail: String): String? = when {
    email.isEmpty() -> null
    !EMAIL.matches(email) -> "\"$email\" doesn't look like an email address."
    email == myEmail.lowercase() -> "That's your own email. Use the other person's."
    else -> null
}

data class DebtInput(val amount: Double, val type: String, val person: String, val date: String, val notes: String, val email: String)

sealed interface DebtCheck {
    data class Ok(val input: DebtInput) : DebtCheck
    data class Invalid(val message: String) : DebtCheck
}

fun checkDebt(amount: String, type: String, person: String, date: String, notes: String, email: String, myEmail: String): DebtCheck {
    val n = jsParseFloat(amount)
    val name = sanitize(person.trim(), 100)
    val mail = email.trim().lowercase()
    if (n.isNaN() || n <= 0) return DebtCheck.Invalid("Enter a valid amount.")
    if (name.isEmpty()) return DebtCheck.Invalid("Enter person name.")
    if (date.isEmpty()) return DebtCheck.Invalid("Select a date.")
    emailError(mail, myEmail)?.let { return DebtCheck.Invalid(it) }
    return DebtCheck.Ok(DebtInput(n, type, name, date, sanitize(notes.trim(), 500), mail))
}

// ── Rows ──

/** The claim line: what the person it's shared with says was paid. */
fun claimText(d: Debt, fmt: (Double) -> String): String {
    val c = d.claim() ?: return ""
    val said = fmt(c.amount) + if (c.settles) ", all of it" else ""
    return if (d.shared) "${if (d.type == I_OWE) "You said you paid" else "You said ${d.person} paid you"} $said · waiting for them"
    else "${d.person} says ${if (d.type == THEY_OWE) "they paid you" else "you paid them"} $said"
}

fun openMeta(d: Debt, today: LocalDate, fmt: (Double) -> String): String = listOf(
    if (d.type == THEY_OWE) "Owes you" else "You owe",
    shortDay(d.date, today),
    if (d.shared) "Added by them" else if (d.email.isNotEmpty()) "Shared" else "",
    claimText(d, fmt),
    if (d.pending) "Waiting to sync" else "",
).filter { it.isNotEmpty() }.joinToString(" · ")

fun settledMeta(d: Debt, today: LocalDate): String {
    val meta = listOf(if (d.type == THEY_OWE) "Paid you back" else "You paid back", if (d.settledDate.isNotEmpty()) shortDay(d.settledDate, today) else "")
        .filter { it.isNotEmpty() }.joinToString(" · ")
    return if (d.shared) "$meta · Added by them" else meta
}

/** The Settled list: latest settled first. */
fun settledDebts(debts: List<Debt>) = debts.filter { it.settled }.sortedByDescending { it.settledDate }

// ── The payment dialog ──

/** debt.js › parseAmount: digits and dots only, so "₹1,268" reads 1268. */
fun parsePayAmount(v: String): Double? = jsParseFloat(v.replace(Regex("[^\\d.]"), "")).takeUnless { it.isNaN() }

private fun paysOff(n: Double, left: Double) = n > 0 && n >= left - 0.001 && n <= left + 0.001

fun payHint(d: Debt, n: Double?, fmt: (Double) -> String): String {
    val left = d.left()
    val after = if (d.shared) " ${d.person} confirms it, then it counts." else ""
    return when {
        n == null || !(n > 0) -> ""
        n > left + 0.001 -> "That's more than the ${fmt(left)} left."
        paysOff(n, left) -> if (d.shared) "That's all of it.$after" else "Pays it off: it moves to Settled."
        else -> "${fmt(left - n)} will be left.$after"
    }
}

fun payButton(d: Debt, n: Double?): String =
    if (d.shared) "Tell ${d.person}" else if (n != null && paysOff(n, d.left())) "Save and settle" else "Save"

fun payTitle(d: Debt) = if (d.type == THEY_OWE) "${d.person} paid you" else "You paid ${d.person}"

private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** confirmDebtSettle's checks; null when [n] can be paid on [date]. */
fun payError(d: Debt, n: Double?, date: String, fmt: (Double) -> String): String? {
    if (!isoDate.matches(date)) return "Pick the date it was paid."
    return when (val r = payDebt(d.amount, d.paid, n ?: Double.NaN, date)) {
        is PayResult.Ok -> null
        PayResult.NotPositive -> "Enter the amount paid."
        is PayResult.TooMuch -> "That's more than the ${fmt(r.left)} left."
    }
}

// ── Reopening ──

/** web quirk: §9.9 — a debt paid off by payments reopens with its last payment taken off. Null: `paid` stays. */
fun reopenPaid(d: Debt): List<Payment>? = if (d.left() == 0.0 && d.paid.isNotEmpty()) d.paid.dropLast(1) else null

fun reopenMessage(d: Debt, today: LocalDate, fmt: (Double) -> String): String {
    val last = if (reopenPaid(d) != null) d.paid.last() else null
    return if (last != null) "${fmt(last.amount)} with ${d.person} moves back to Open, and the payment of ${fmt(last.amount)} on ${shortDay(last.date, today)} is taken off."
    else "${fmt(d.left())} with ${d.person} moves back to Open."
}
