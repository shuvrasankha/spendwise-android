package com.spendwise.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Festivals and public holidays for the AI budget, from Google Calendar's public holiday feed for the
// country of the user's currency. Nothing about the user goes out: the request names only the country.

/** Country (from the currency's locale) to Google's holiday calendar; a country not listed gets no feed. */
private val HOLIDAY_CALENDARS = mapOf(
    "IN" to "en.indian", "US" to "en.usa", "GB" to "en.uk", "DE" to "en.german", "BD" to "en.bd",
    "PK" to "en.pk", "LK" to "en.lk", "NP" to "en.np", "AE" to "en.ae", "SA" to "en.sa",
    "JP" to "en.japanese", "AU" to "en.australian", "CA" to "en.canadian", "SG" to "en.singapore",
    "MY" to "en.malaysia", "TH" to "en.th", "ID" to "en.indonesian", "PH" to "en.philippines", "KR" to "en.south_korea",
)

fun holidayFeedUrl(currency: Currency): String? {
    val id = HOLIDAY_CALENDARS[Locale.forLanguageTag(currency.locale).country] ?: return null
    return "https://calendar.google.com/calendar/ical/$id%23holiday%40group.v.calendar.google.com/public/basic.ics"
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** The feed's events in [month] ("YYYY-MM") as "8 Nov: Diwali/Deepavali", by date, each name once. */
fun holidaysIn(ics: String, month: String): List<String> {
    val ym = month.replace("-", "")
    // Unfold: a line starting with a space continues the one before it (RFC 5545)
    val lines = ics.replace("\r\n", "\n").replace("\n ", "").lines()
    val events = mutableListOf<Pair<String, String>>()
    var date = ""
    var name = ""
    for (line in lines) when {
        line == "BEGIN:VEVENT" -> { date = ""; name = "" }
        line.startsWith("DTSTART") -> date = line.substringAfterLast(':').take(8)
        line.startsWith("SUMMARY") -> name = line.substringAfter(':').replace("\\,", ",").replace("\\;", ";").trim()
        line == "END:VEVENT" && date.startsWith(ym) && name.isNotEmpty() -> events += date to name
    }
    return events.sortedBy { it.first }.distinctBy { it.second }.mapNotNull { (d, n) ->
        runCatching { LocalDate.parse("${d.take(4)}-${d.substring(4, 6)}-${d.substring(6, 8)}").format(dayFormat) }.getOrNull()?.let { "$it: $n" }
    }
}
