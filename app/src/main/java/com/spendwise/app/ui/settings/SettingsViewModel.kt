package com.spendwise.app.ui.settings

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.account.AccountDeleter
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.auth.webAuthCode
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.prefs.ThemeMode
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.DEFAULT_REMIND_AT
import com.spendwise.app.domain.REMINDER_ASK
import com.spendwise.app.domain.REMINDER_BLOCKED
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.accountRemindAt
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.friendlyErr
import com.spendwise.app.domain.reminderOnNote
import com.spendwise.app.reminder.ReminderScheduler
import com.spendwise.app.ui.lock.AppLock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currency: Currency = currencyOf(null),
    val name: String = "",
    val email: String = "",
    /** On for this device (scheduled here), and the time shown: the account's, else this device's, else 21:00. */
    val reminderOn: Boolean = false,
    val reminderAt: String = DEFAULT_REMIND_AT,
    val reminderNote: String? = null,
    val photoUrl: String = "",
    val lockOn: Boolean = false,
)

/** Delete account (spec §8.2): confirm → sign in again → delete, with progress and a way to finish after a failure. */
sealed interface Deletion {
    data object None : Deletion
    data class Confirm(val typed: String = "") : Deletion
    data class Reauth(val usesPassword: Boolean, val error: String? = null, val busy: Boolean = false) : Deletion
    data class Working(val label: String, val step: Int, val of: Int) : Deletion
    data class Failed(val message: String) : Deletion
}

const val DELETE_WORD = "DELETE"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: DevicePrefs,
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val deleter: AccountDeleter,
    private val lock: AppLock,
) : ViewModel() {

    /** A note from the last tap (blocked, failed), shown instead of the usual one until the next change. */
    private val tapNote = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        combine(prefs.themeMode, prefs.currency, auth.authState, ::Triple),
        settings.settings().catch { emit(UserSettings()) },
        prefs.reminderAt,
        tapNote,
        prefs.appLock,
    ) { (mode, currency, authState), s, deviceAt, note, lockOn ->
        val user = (authState as? AuthState.SignedIn)?.user
        val at = accountRemindAt(s.remindAt) ?: deviceAt ?: DEFAULT_REMIND_AT
        val usual = when {
            deviceAt != null -> reminderOnNote(at)
            s.remindOn == true -> REMINDER_ASK
            else -> null
        }
        SettingsUiState(mode, currency, user?.displayName.orEmpty(), user?.email.orEmpty(), deviceAt != null, at, note ?: usual, user?.photoUrl.orEmpty(), lockOn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    val widgetTransparency: StateFlow<Int> = prefs.widgetTransparency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setWidgetTransparency(percent: Int) {
        viewModelScope.launch { prefs.setWidgetTransparency(percent) }
    }

    /** The tour shows again at once: the app is gated on this flag. */
    fun replayTour() {
        viewModelScope.launch { prefs.setTourSeen(false) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch { prefs.setCurrency(code) }
    }

    /** The auth gate shows the sign-in screen once this lands. */
    fun logOut() {
        viewModelScope.launch { auth.signOut() }
    }

    /** Called once the phone's check passed: turning it off needs the same check as opening the app. */
    fun setLock(on: Boolean) {
        viewModelScope.launch { lock.setOn(on) }
    }

    // ── Daily reminder (reminders.js › enable, disable, onTimeChange) ──

    /** Notifications must be allowed already; on Android 13+ the screen asks first and passes the answer. */
    fun turnReminderOn(granted: Boolean) = viewModelScope.launch {
        if (!granted || !scheduler.permitted()) return@launch tapNote.update { REMINDER_BLOCKED }
        tapNote.update { null }
        val at = state.value.reminderAt
        runCatching { settings.saveRemindOn(true) }
        scheduler.schedule(at)
    }

    fun turnReminderOff() = viewModelScope.launch {
        tapNote.update { null }
        runCatching { settings.saveRemindOn(false) }
        scheduler.cancel()
    }

    fun setReminderTime(at: String) = viewModelScope.launch {
        tapNote.update { null }
        runCatching { settings.saveRemindAt(at) }
        if (state.value.reminderOn) scheduler.schedule(at)
    }

    // ── Delete account ──

    private val _deletion = MutableStateFlow<Deletion>(Deletion.None)
    val deletion: StateFlow<Deletion> = _deletion.asStateFlow()

    fun askDelete() = _deletion.update { Deletion.Confirm() }
    fun typeConfirm(text: String) = _deletion.update { if (it is Deletion.Confirm) it.copy(typed = text) else it }
    fun cancelDelete() = _deletion.update { if (it is Deletion.Working) it else Deletion.None }

    /** After typing DELETE, or "Try again" after a failure: always sign in again first (spec §8.2 step 4). */
    fun confirmDelete() = _deletion.update { Deletion.Reauth(auth.usesPassword) }

    fun reauthWithPassword(password: String) = reauth { auth.reauthenticateWithPassword(password) }
    fun reauthWithGoogle(activityContext: Context) = reauth { auth.reauthenticateWithGoogle(activityContext) }

    private fun reauth(signIn: suspend () -> Unit) = viewModelScope.launch {
        val asked = _deletion.value as? Deletion.Reauth ?: return@launch
        _deletion.update { asked.copy(error = null, busy = true) }
        try {
            signIn()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            return@launch _deletion.update { asked.copy(busy = false) }
        } catch (e: Exception) {
            return@launch _deletion.update { asked.copy(error = friendlyErr(webAuthCode(e)), busy = false) }
        }
        deleteAll()
    }

    private suspend fun deleteAll() {
        var current = "data"
        try {
            // Finishing matters more than the screen: leaving Settings mustn't stop it halfway
            withContext(NonCancellable) {
                deleter.deleteEverything { label, step, of ->
                    current = label
                    _deletion.update { Deletion.Working(label, step, of) }
                }
            }
            // Signed out now: the auth gate shows sign-in
        } catch (e: Exception) {
            _deletion.update {
                Deletion.Failed(
                    if (e is FirebaseAuthRecentLoginRequiredException || current == "account") {
                        "Your data is deleted, but the account itself wasn't. Try again to finish."
                    } else {
                        "Deleting stopped while removing your $current, so some of your data is already gone. Your account is still here. Check your connection, then try again to finish."
                    },
                )
            }
        }
    }
}
