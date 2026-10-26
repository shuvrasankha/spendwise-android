package com.spendwise.app.ui.format

import android.icu.text.NumberFormat
import android.icu.util.ULocale
import com.spendwise.app.domain.Currency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** currency.js › fmt: symbol, a space, then the amount in the currency's locale with 2 decimals (₹ 1,00,000.00). */
fun formatMoney(amount: Double, currency: Currency): String {
    // ICU, unlike java.text on the JVM, groups en-IN in lakhs like the browser does
    val format = NumberFormat.getNumberInstance(ULocale.forLanguageTag(currency.locale)).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return currency.symbol + " " + format.format(amount)
}

private val shortDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

/** helpers.js › formatDate: "26 Oct 2026". Falls back to the stored text for a malformed date. */
fun formatDate(date: String): String = runCatching { LocalDate.parse(date).format(shortDate) }.getOrDefault(date)

/** dashboard.js › moneyWhole: symbol, a space, then Math.round(n) in the currency's locale (₹ 1,00,000). */
fun formatWhole(amount: Double, currency: Currency): String =
    currency.symbol + " " + NumberFormat.getIntegerInstance(ULocale.forLanguageTag(currency.locale)).format(Math.round(amount))

/** helpers.js › plural: "1 entry", "3 entries". */
fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
