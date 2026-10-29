package com.spendwise.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    /** The time the daily reminder is scheduled for on this device, or null when it's off here. */
    val reminderAt: Flow<String?> = store.data.map { it[REMINDER_AT] }

    suspend fun setReminderAt(at: String?) {
        store.edit { if (at == null) it.remove(REMINDER_AT) else it[REMINDER_AT] = at }
    }

    /** "uid:YYYY-MM-DD" once /push/logged went through today (reminders.js › LOGGED_KEY). */
    val loggedToday: Flow<String?> = store.data.map { it[LOGGED_TODAY] }

    suspend fun setLoggedToday(key: String) {
        store.edit { it[LOGGED_TODAY] = key }
    }

    /** "Continue" in the AI dialog (insights-v2.js › aiAdvisorConsentV3); per device, like the web. */
    val aiConsent: Flow<Boolean> = store.data.map { it[AI_CONSENT] == true }

    suspend fun setAiConsent() {
        store.edit { it[AI_CONSENT] = true }
    }

    /** This device's endpoint ("fcm:<token>") while the Worker sends its reminder, else null (local reminder). */
    val pushEndpoint: Flow<String?> = store.data.map { it[PUSH_ENDPOINT] }

    /** The reminder time the Worker has for [pushEndpoint]. */
    val pushAt: Flow<String?> = store.data.map { it[PUSH_AT] }

    suspend fun setPushEndpoint(endpoint: String?, at: String?) {
        store.edit {
            if (endpoint == null) it.remove(PUSH_ENDPOINT) else it[PUSH_ENDPOINT] = endpoint
            if (at == null) it.remove(PUSH_AT) else it[PUSH_AT] = at
        }
    }

    /** The body the Worker last took for [path] ("uid\nbody"), so an unchanged one isn't resent (reminders.js › pushSync). */
    fun pushSynced(path: String): Flow<String?> = store.data.map { it[stringPreferencesKey("push_sync:$path")] }

    suspend fun setPushSynced(path: String, value: String?) {
        val key = stringPreferencesKey("push_sync:$path")
        store.edit { if (value == null) it.remove(key) else it[key] = value }
    }

    /**
     * The bell's "seen" or "cleared" ids on this device, newest last (notifications.js › SEEN_KEY/CLEARED_KEY + uid).
     * The bell trusts this copy, so a clear holds even when the account copy (settings.notif) can't be written.
     */
    fun notifIds(kind: String, uid: String): Flow<List<String>> =
        store.data.map { prefs -> prefs[stringPreferencesKey("notif_$kind:$uid")]?.split('\n')?.filter { it.isNotEmpty() }.orEmpty() }

    suspend fun setNotifIds(kind: String, uid: String, ids: List<String>) {
        store.edit { it[stringPreferencesKey("notif_$kind:$uid")] = ids.joinToString("\n") }
    }

    /** "I Understand & Continue" in the CSV upload dialog (csv-upload.js › csvUploadConsent); per device. */
    val csvConsent: Flow<Boolean> = store.data.map { it[CSV_CONSENT] == true }

    suspend fun setCsvConsent() {
        store.edit { it[CSV_CONSENT] = true }
    }

    /** The coach's conversation as JSON (SavedChat), kept a day on this device only, like the web's saveChat. */
    val coachChat: Flow<String?> = store.data.map { it[COACH_CHAT] }

    suspend fun setCoachChat(json: String?) {
        store.edit { if (json == null) it.remove(COACH_CHAT) else it[COACH_CHAT] = json }
    }

    /** Account deletion: nothing of the account stays on the device (spec §8.2). */
    suspend fun clear() {
        store.edit { it.clear() }
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
        val REMINDER_AT = stringPreferencesKey("reminder_at")
        val LOGGED_TODAY = stringPreferencesKey("logged_today")
        val AI_CONSENT = booleanPreferencesKey("ai_consent")
        val CSV_CONSENT = booleanPreferencesKey("csv_consent")
        val PUSH_ENDPOINT = stringPreferencesKey("push_endpoint")
        val PUSH_AT = stringPreferencesKey("push_at")
        val COACH_CHAT = stringPreferencesKey("coach_chat")
    }
}
