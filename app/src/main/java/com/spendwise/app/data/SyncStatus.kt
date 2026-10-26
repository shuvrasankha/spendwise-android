package com.spendwise.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.spendwise.app.data.expenses.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The header's offline and pending-sync state (spec §1.0, §7.3). Firestore queues offline writes itself,
 * so "pending" is the entries whose writes haven't reached the server (metadata.hasPendingWrites).
 */
@Singleton
class SyncStatus @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val expenses: ExpenseRepository,
    private val income: IncomeRepository,
    private val debts: DebtRepository,
) {
    /** Entries waiting to sync. A load error counts as none, so the badge never blocks anything. */
    val pending: Flow<Int> = combine(
        expenses.expenses().catch { emit(emptyList()) },
        income.income().catch { emit(emptyList()) },
        debts.debts().catch { emit(emptyList()) },
    ) { e, i, d -> e.count { it.pending } + i.count { it.pending } + d.count { it.pending } }
        .distinctUntilChanged()

    /** Whether the device has a validated internet connection. */
    val online: Flow<Boolean> = callbackFlow {
        val manager = checkNotNull(context.getSystemService<ConnectivityManager>())
        fun now() = manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(now()) }
            override fun onLost(network: Network) { trySend(now()) }
        }
        trySend(now())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
