package com.spendwise.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.spendwise.app.domain.CURRENCIES
import com.spendwise.app.domain.Currency
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
    }
}
