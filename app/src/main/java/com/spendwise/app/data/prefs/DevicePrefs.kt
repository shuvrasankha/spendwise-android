package com.spendwise.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.spendwise.app.domain.CURRENCIES
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.DEFAULT_PAYMENT
import com.spendwise.app.domain.PAYMENT_METHODS
import com.spendwise.app.domain.currencyOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** System is the default; the web cycles system → light → dark (spec §2.11). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Per-device settings, never synced, like the web's localStorage (spec §2.11, §2.12, §3.6). */
@Singleton
class DevicePrefs @Inject constructor(private val store: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        when (prefs[THEME]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val currency: Flow<Currency> = store.data.map { currencyOf(it[CURRENCY]) }

    /** The Add form starts on the last payment method used (dashboard.js › lastPayment). */
    val lastPayment: Flow<String> = store.data.map { prefs ->
        prefs[LAST_PAYMENT]?.takeIf { it in PAYMENT_METHODS } ?: DEFAULT_PAYMENT
    }

    suspend fun setLastPayment(payment: String) {
        store.edit { it[LAST_PAYMENT] = payment }
    }

    /** "Skip month" on a due bill, as "seriesKey:YYYY-MM" (spec §9.4, Q5): per device, like the web. */
    val skippedBills: Flow<Set<String>> = store.data.map { it[SKIPPED_BILLS].orEmpty() }

    suspend fun skipBill(key: String, month: String) {
        store.edit { it[SKIPPED_BILLS] = it[SKIPPED_BILLS].orEmpty() + "$key:$month" }
    }

    /** Like the web, System removes the key rather than storing it. */
    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit {
            when (mode) {
                ThemeMode.SYSTEM -> it.remove(THEME)
                ThemeMode.LIGHT -> it[THEME] = "light"
                ThemeMode.DARK -> it[THEME] = "dark"
            }
        }
    }

    /** Unknown codes are ignored, like currency.js › setCurrency. */
    suspend fun setCurrency(code: String) {
        if (CURRENCIES.none { it.code == code }) return
        store.edit { it[CURRENCY] = code }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val CURRENCY = stringPreferencesKey("currency")
        val LAST_PAYMENT = stringPreferencesKey("last_payment")
        val SKIPPED_BILLS = stringSetPreferencesKey("skipped_bills")
    }
}
