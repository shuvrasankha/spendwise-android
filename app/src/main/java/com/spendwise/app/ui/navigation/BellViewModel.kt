package com.spendwise.app.ui.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.DebtRepository
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.forUser
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.NotifItem
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.mergeIds
import com.spendwise.app.domain.notificationsFrom
import com.spendwise.app.domain.pushTagsFor
import com.spendwise.app.reminder.Notifications
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [items]: not cleared, newest first. [all]: before clearing, so an empty list can say "all caught up". */
data class BellState(val items: List<NotifItem> = emptyList(), val unread: Set<String> = emptySet(), val anyAtAll: Boolean = false, val seen: List<String> = emptyList(), val cleared: List<String> = emptyList())

/**
 * The header's bell (spec §7.6, notifications.js): built from debts. Seen and cleared are this device's copy
 * joined with the account's (settings.notif), as the web joins localStorage with it.
 */
@HiltViewModel
class BellViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    debts: DebtRepository,
    private val settings: SettingsRepository,
    private val prefs: DevicePrefs,
    private val auth: AuthRepository,
) : ViewModel() {

    private val local = auth.forUser(emptyList<String>() to emptyList()) { u ->
        combine(prefs.notifIds(SEEN, u.uid), prefs.notifIds(CLEARED, u.uid)) { seen, cleared -> seen to cleared }
    }

    // A load error shows no items, as the web's fetches fall back to none
    val state: StateFlow<BellState> = combine(debts.debts().catch { emit(emptyList()) }, settings.settings().catch { emit(UserSettings()) }, local) { d, s, (localSeen, localCleared) ->
        val seen = mergeIds(localSeen, s.notifSeen)
        val cleared = mergeIds(localCleared, s.notifCleared)
        val all = notificationsFrom(d.filter { it.shared }, d.filter { !it.shared })
        val items = all.filter { it.id !in cleared }
        BellState(items, items.map { it.id }.filter { it !in seen }.toSet(), all.isNotEmpty(), seen, cleared)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BellState())

    val currency: StateFlow<Currency> = prefs.currency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    /** open(): everything listed counts as seen and leaves the tray; written only when something was new. */
    fun markSeen() {
        val s = state.value
        Notifications.cancel(context, pushTagsFor(s.items.map { it.id }))
        if (s.unread.isEmpty()) return
        save(mergeIds(s.seen, s.items.map { it.id }), s.cleared)
    }

    /** clear(): off the list (the debts stay), and seen. */
    fun clear(ids: List<String>) {
        val s = state.value
        if (ids.isEmpty()) return
        save(mergeIds(s.seen, ids), mergeIds(s.cleared, ids))
        Notifications.cancel(context, pushTagsFor(ids))
    }

    /** This device first, so the list changes even if the account write is refused or offline; then the account. */
    private fun save(seen: List<String>, cleared: List<String>) {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            prefs.setNotifIds(SEEN, uid, seen)
            prefs.setNotifIds(CLEARED, uid, cleared)
        }
        settings.saveNotif(seen, cleared)
    }

    private companion object {
        const val SEEN = "seen"
        const val CLEARED = "cleared"
    }
}
