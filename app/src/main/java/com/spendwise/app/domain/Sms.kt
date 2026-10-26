package com.spendwise.app.domain

import java.time.LocalDate

// Ported from sms-parse.js (spec §7.4): reads a bank or UPI SMS on the device, no AI, nothing sent.
// null when the text isn't a completed transaction (OTPs, due reminders, failed or requested payments).

sealed interface SmsEntry {
    val amount: Double
    val date: String
    data class Spent(override val amount: Double, override val date: String, val category: String, val payment: String, val cardName: String, val description: String) : SmsEntry
    data class Earned(override val amount: Double, override val date: String, val source: String, val paymentType: String, val bank: String) : SmsEntry
}

private val I = RegexOption.IGNORE_CASE
private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
// JS's \s also covers Unicode spaces; Java's doesn't
private val SPACES = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

private val SKIP = Regex("""\b(otp|one[- ]time password|verification code|will be (debited|credited)|is due|due (on|date|by)|min(imum)? amount|request(ed)?|collect request|mandate|failed|declined|unsuccessful)\b""", I)
private val ACCOUNT = Regex("""\b(a/c|acct|account|card|upi|vpa|ref|txn|avl|bank|neft|imps|rtgs|wallet)\b|@""", I)
private val DEBIT = Regex("""\b(debited|spent|paid|sent|withdrawn|withdrawal|purchase|charged|deducted|debit)\b""", I)
private val CREDIT = Regex("""\b(credited|received|deposited|refunded|refund|cashback|credit(?!\s*card))\b""", I)
private val AMOUNT = Regex("""(?:rs|inr|₹)[.:]?\s*([\d,]+(?:\.\d{1,2})?)""", I)
// SBI leaves the currency out: "A/C X1234 debited by 250.0"
private val BARE_AMOUNT = Regex("""\b(?:debited|credited)\s+(?:by|for|with)\s+([\d,]+(?:\.\d{1,2})?)\b""", I)
private val NOT_AMOUNT = Regex("""(bal(ance)?|lmt|limit|avl|available|due|outstanding)[\s.:a-z]*$""", I)

private val BANKS = listOf(
    "HDFC" to """\bhdfc\b""", "ICICI" to """\bicici\b""", "SBI" to """\b(sbi|state bank)\b""", "Axis" to """\baxis\b""",
    "Kotak" to """\bkotak\b""", "Yes Bank" to """\byes bank\b""", "IDFC First" to """\bidfc\b""", "IndusInd" to """\bindusind\b""",
    "Federal" to """\bfederal bank\b""", "PNB" to """\b(pnb|punjab national)\b""", "Bank of Baroda" to """\b(bob|bank of baroda)\b""",
    "Canara" to """\bcanara\b""", "Union Bank" to """\bunion bank\b""", "AU" to """\bau (small finance )?bank\b""",
    "RBL" to """\brbl\b""", "Paytm" to """\bpaytm\b""", "Airtel Payments" to """\bairtel payments\b""",
).map { (name, re) -> name to Regex(re, I) }

// First match wins, so more specific names come first (jiomart before jio); matched against the lowercased name
private val SMS_CATEGORIES = listOf(
    "Groceries" to """bigbasket|blinkit|zepto|dmart|instamart|grofers|jiomart|reliance fresh|grocer|supermarket""",
    "Food & Dining" to """swiggy|zomato|domino|pizza|mcdonald|kfc|starbucks|cafe|restaurant|eatsure|burger""",
    "Travel" to """makemytrip|goibibo|indigo|air ?india|vistara|akasa|cleartrip|airbnb|\boyo\b|yatra""",
    "Transport" to """\buber\b|\bola\b|rapido|metro|irctc|redbus|fuel|petrol|hpcl|bpcl|iocl|indian ?oil|fastag|parking""",
    "Bills & Utilities" to """netflix|spotify|hotstar|prime ?video|youtube|apple\.com|google ?play|sonyliv|zee5""",
    "Shopping" to """amazon|flipkart|myntra|ajio|meesho|nykaa|croma|decathlon|ikea""",
    "Bills & Utilities" to """airtel|\bjio\b|vodafone|\bvi\b|bsnl|electricity|bescom|tata ?power|broadband|recharge|\bbill\b""",
    "Health" to """apollo|pharmeasy|netmeds|1mg|medplus|hospital|clinic|pharmacy|practo""",
    "Entertainment" to """bookmyshow|\bpvr\b|inox|steam|playstation""",
    "Savings & Investments" to """zerodha|groww|upstox|kuvera|mutual ?fund|\bsip\b|\bnps\b|\bppf\b""",
    "Education" to """udemy|coursera|byju|unacademy|school|college|tuition""",
    "Home & Rent" to """\brent\b|nobroker""",
).map { (name, re) -> name to Regex(re) }

private fun number(s: String) = s.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

private fun amountOf(text: String): Double? {
    for (m in AMOUNT.findAll(text)) {
        if (NOT_AMOUNT.containsMatchIn(text.substring(maxOf(0, m.range.first - 25), m.range.first))) continue
        number(m.groupValues[1])?.let { return it }
    }
    return BARE_AMOUNT.find(text)?.let { number(it.groupValues[1]) }
}

private val ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
private val NUMERIC = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""")
private val NAMED = Regex("""\b(\d{1,2})[- ]?([a-z]{3})[a-z]*[-, ]*(\d{2,4})\b""", I)

/** Indian SMS dates: 21-10-26, 21/10/2026, 21-Oct-26, 21 Oct 2026, 2026-10-21. A real, non-future date, else today. */
private fun dateOf(text: String, today: LocalDate): String {
    val ymd: List<Int>? = ISO.find(text)?.groupValues?.let { listOf(it[1].toInt(), it[2].toInt(), it[3].toInt()) }
        ?: NUMERIC.find(text)?.groupValues?.let { listOf(it[3].toInt(), it[2].toInt(), it[1].toInt()) }
        ?: NAMED.find(text)?.groupValues?.takeIf { it[2].lowercase() in MONTHS }
            ?.let { listOf(it[3].toInt(), MONTHS.indexOf(it[2].lowercase()) + 1, it[1].toInt()) }
    if (ymd != null) {
        val year = if (ymd[0] < 100) ymd[0] + 2000 else ymd[0]
        val date = runCatching { LocalDate.of(year, ymd[1], ymd[2]) }.getOrNull()
        if (date != null && date <= today) return date.toString()
    }
    return today.toString()
}

private const val STOP = """(?=\s+(?:on|ref|refno|via|using|upi|avl|from|for|with|txn|is|has|was|thru|through|dated|info)\b|\s*[.,;:(]|\s+\d|$)"""
private const val NAME = """([A-Za-z][\w&'.* -]{1,40}?)"""
private val VPA_TO = Regex("""\b(?:to|at)\s+(?:vpa\s+)?([\w.-]+)@[\w.-]+""", I)
private val VPA_FROM = Regex("""\b(?:from|by)\s+(?:vpa\s+)?([\w.-]+)@[\w.-]+""", I)
private const val NOT_NAME = """(?!a/c|ac\b|acct|your|account|card|neft|imps|rtgs|upi|transfer|bank\b)"""
private val NAME_TO = Regex("""\b(?:at|to|towards)\s+$NOT_NAME$NAME$STOP""", I)
private val NAME_FROM = Regex("""\b(?:from|by)\s+$NOT_NAME$NAME$STOP""", I)
// ICICI: "debited for Rs 499.00 on 21-Oct-26; Netflix credited"
private val NAME_CREDITED = Regex(""";\s*$NAME\s+credited\b""", I)

/** Who was paid (debit) or who paid (credit). */
private fun partyOf(text: String, debit: Boolean): String {
    var name = (if (debit) VPA_TO else VPA_FROM).find(text)?.groupValues?.get(1)
        ?: (if (debit) NAME_TO else NAME_FROM).find(text)?.groupValues?.get(1)
        ?: if (debit) NAME_CREDITED.find(text)?.groupValues?.get(1) else null
    if (name == null || Regex("""\d{5,}""").containsMatchIn(name)) return "" // QR codes and account numbers aren't names
    name = name.replace(Regex("[._-]+"), " ").trim()
    return if (name == name.uppercase() || name == name.lowercase()) {
        name.lowercase().replace(Regex("""\b\w""")) { it.value.uppercase() }
    } else name
}

private fun paymentOf(text: String) = when {
    Regex("credit card", I).containsMatchIn(text) -> "Credit Card"
    Regex("""debit card|\batm\b|\bcard\b""", I).containsMatchIn(text) -> "Debit Card"
    Regex("""\bupi\b|\bvpa\b|@""", I).containsMatchIn(text) -> "UPI"
    Regex("""\b(neft|imps|rtgs|net ?banking)\b""", I).containsMatchIn(text) -> "Net Banking"
    else -> "Other"
}

fun parseSms(raw: String, today: LocalDate): SmsEntry? {
    val text = raw.replace(SPACES, " ").trim()
    if (text.isEmpty() || SKIP.containsMatchIn(text) || !ACCOUNT.containsMatchIn(text)) return null
    val amount = amountOf(text) ?: return null
    val d = DEBIT.find(text)?.range?.first ?: -1
    val c = CREDIT.find(text)?.range?.first ?: -1
    if (d < 0 && c < 0) return null
    val debit = d >= 0 && (c < 0 || d < c) // "debited ... credited to beneficiary" is still a debit
    val date = dateOf(text, today)
    val party = partyOf(text, debit)
    val bank = BANKS.firstOrNull { it.second.containsMatchIn(text) }?.first ?: ""
    if (debit) {
        val payment = paymentOf(text)
        val category = SMS_CATEGORIES.firstOrNull { it.second.containsMatchIn(party.lowercase()) }?.first ?: "Other"
        return SmsEntry.Spent(amount, date, category, payment, if (payment.endsWith("Card")) bank else "", party.ifEmpty { "Bank payment" })
    }
    val source = when {
        Regex("salary|sal cr", I).containsMatchIn(text) -> "Salary"
        Regex("refund|reversed", I).containsMatchIn(text) -> "Refund"
        Regex("interest", I).containsMatchIn(text) -> "Interest"
        Regex("cashback", I).containsMatchIn(text) -> "Cashback"
        else -> party.ifEmpty { "Bank credit" }
    }
    return SmsEntry.Earned(amount, date, source, "Online", bank)
}

const val SMS_READ = "Read from your SMS on this device. Check it, fix anything that's off, then save."
const val SMS_UNREAD = "Couldn't read that as a bank SMS, so it's in Description. Add the amount and category, then save."
