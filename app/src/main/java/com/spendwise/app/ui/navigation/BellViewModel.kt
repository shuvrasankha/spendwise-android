package com.spendwise.app.ui.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.DebtRepository
import com.spendwise.app.data.SettingsRepository
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
import javax.inject.Inject

/** [items]: not cleared, newest first. [all]: before clearing, so an empty list can say "all caught up". */
data class BellState(val items: List<NotifItem> = emptyList(), val unread: Set<String> = emptySet(), val anyAtAll: Boolean = false, val seen: List<String> = emptyList(), val cleared: List<String> = emptyList())

/** The header's bell (spec §7.6, notifications.js): built from debts; seen and cleared live in settings.notif. */
@HiltViewModel
class BellViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    debts: DebtRepository,
    private val settings: SettingsRepository,
    prefs: DevicePrefs,
) : ViewModel() {

    // A load error shows no items, as the web's fetches fall back to none
    val state: StateFlow<BellState> = combine(debts.debts().catch { emit(emptyList()) }, settings.settings().catch { emit(UserSettings()) }) { d, s ->
        val all = notificationsFrom(d.filter { it.shared }, d.filter { !it.shared })
        val items = all.filter { it.id !in s.notifCleared }
        BellState(items, items.map { it.id }.filter { it !in s.notifSeen }.toSet(), all.isNotEmpty(), s.notifSeen, s.notifCleared)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BellState())

    val currency: StateFlow<Currency> = prefs.currency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    /** open(): everything listed counts as seen and leaves the tray; written only when something was new. */
    fun markSeen() {
        val s = state.value
        Notifications.cancel(context, pushTagsFor(s.items.map { it.id }))
        if (s.unread.isEmpty()) return
        settings.saveNotif(mergeIds(s.seen, s.items.map { it.id }), s.cleared)
    }

    /** clear(): off the list (the debts stay), and seen. */
    fun clear(ids: List<String>) {
        val s = state.value
        if (ids.isEmpty()) return
        settings.saveNotif(mergeIds(s.seen, ids), mergeIds(s.cleared, ids))
        Notifications.cancel(context, pushTagsFor(ids))
    }
}
