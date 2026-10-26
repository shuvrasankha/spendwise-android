package com.spendwise.app.reminder

import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.reminderSteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps this device's reminder in step with settings/{uid} (spec §7.5): on sign-in and on every change. Signing
 * out stops it here, since there's no account to check entries against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ReminderSync @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val prefs: DevicePrefs,
    private val scheduler: ReminderScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        auth.authState
            .filter { it !is AuthState.Loading }
            .distinctUntilChanged { a, b -> (a as? AuthState.SignedIn)?.user?.uid == (b as? AuthState.SignedIn)?.user?.uid }
            .flatMapLatest { state ->
                if (state is AuthState.SignedIn) settings.settings().catch { }
                else {
                    scheduler.cancel()
                    flowOf()
                }
            }
            .onEach { runCatching { apply(it) } } // one failed step mustn't stop later syncs
            .launchIn(scope)
    }

    private suspend fun apply(s: UserSettings) {
        val steps = reminderSteps(s.remindOn, s.remindAt, prefs.reminderAt.first(), scheduler.permitted())
        if (steps.cancel) scheduler.cancel()
        steps.schedule?.let { scheduler.schedule(it) }
        if (steps.uploadOn) runCatching { settings.saveRemindOn(true) }
        steps.uploadAt?.let { at -> runCatching { settings.saveRemindAt(at) } }
    }
}
