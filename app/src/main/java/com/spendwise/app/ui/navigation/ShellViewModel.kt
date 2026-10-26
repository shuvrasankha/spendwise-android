package com.spendwise.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.SyncStatus
import com.spendwise.app.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How long "Back online!" shows, as the web's online indicator (network-status.js). */
const val BACK_ONLINE_MS = 3_000L

/**
 * The header's status line (offline-queue.js, network-status.js), or null when there's nothing to say.
 * [backOnline]: the connection just came back.
 */
fun syncLine(online: Boolean, pending: Int, backOnline: Boolean): String? {
    val count = if (pending > 99) "99+" else pending.toString()
    return when {
        !online -> if (pending > 0) "You're offline. $count pending" else "You're offline."
        backOnline -> "Back online! Syncing your changes..."
        pending > 0 -> "$count pending"
        else -> null
    }
}

data class HeaderState(val name: String, val email: String, val status: String?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShellViewModel @Inject constructor(private val auth: AuthRepository, sync: SyncStatus) : ViewModel() {

    // true for a few seconds after going from offline to online
    private val backOnline = sync.online.withIndex().transformLatest { (i, on) ->
        if (on && i > 0) {
            emit(true)
            delay(BACK_ONLINE_MS)
        }
        emit(false)
    }

    val header: StateFlow<HeaderState> = combine(sync.online, sync.pending, backOnline) { online, pending, back ->
        val user = auth.currentUser
        HeaderState(user?.displayName.orEmpty(), user?.email.orEmpty(), syncLine(online, pending, back))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeaderState("", "", null).withUser())

    private fun HeaderState.withUser() = auth.currentUser?.let { copy(name = it.displayName, email = it.email) } ?: this

    fun logOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
