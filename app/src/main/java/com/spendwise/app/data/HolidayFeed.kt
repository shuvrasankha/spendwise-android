package com.spendwise.app.data

import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.holidayFeedUrl
import com.spendwise.app.domain.holidaysIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** This month's festivals and holidays from the web; empty when offline or the country has no feed. */
@Singleton
class HolidayFeed @Inject constructor() {
    suspend fun forMonth(currency: Currency, month: String): List<String> {
        val url = holidayFeedUrl(currency) ?: return emptyList()
        return try {
            withContext(Dispatchers.IO) {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                try {
                    if (conn.responseCode != 200) emptyList() else holidaysIn(conn.inputStream.bufferedReader().use { it.readText() }, month)
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList() // the AI still plans, from its own sense of the month
        }
    }
}
