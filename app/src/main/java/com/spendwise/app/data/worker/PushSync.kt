package com.spendwise.app.data.worker

import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.UpcomingBill
import com.spendwise.app.domain.pushBills
import com.spendwise.app.domain.pushMonthTotals
import com.spendwise.app.domain.todayStr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import javax.inject.Inject
import javax.inject.Singleton

fun billsBody(bills: List<UpcomingBill>, symbol: String): JsonObject = buildJsonObject {
    putJsonArray("bills") {
        bills.forEach { b -> add(buildJsonObject { put("key", b.key); put("title", b.title); put("amount", b.amount); put("due", b.due) }) }
    }
    put("symbol", symbol)
}

fun monthsBody(totals: Map<String, Long>, symbol: String): JsonObject = buildJsonObject {
    putJsonObject("totals") { totals.forEach { (m, v) -> put(m, v) } }
    put("symbol", symbol)
}

/**
 * Sends the Worker the next due bills and this and last month's spending (dashboard.js › syncPush), so its cron can
 * alert on bills and summarise a month. Only from server-fresh data, and only when it changed (reminders.js › pushSync).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PushSync @Inject constructor(
    private val auth: AuthRepository,
    private val expenses: ExpenseRepository,
    private val prefs: DevicePrefs,
    private val client: WorkerClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        auth.authState
            .flatMapLatest { state ->
                if (state !is AuthState.SignedIn) flowOf()
                else combine(expenses.expenses(serverOnly = true), prefs.skippedBills, prefs.currency) { all, skipped, currency ->
                    val today = todayStr()
                    val bills = pushBills(all, today) { key, month -> "$key:$month" in skipped }
                    state.user.uid to listOf(
                        "/push/bills" to billsBody(bills, currency.symbol),
                        "/push/months" to monthsBody(pushMonthTotals(all, today), currency.symbol),
                    )
                }.catch { }
            }
            .onEach { (uid, posts) -> posts.forEach { (path, body) -> send(uid, path, body) } }
            .launchIn(scope)
    }

    private suspend fun send(uid: String, path: String, body: JsonObject) {
        val sent = "$uid\n$body"
        if (prefs.pushSynced(path).first() == sent) return
        try {
            if (client.post(path, body).ok) prefs.setPushSynced(path, sent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline: the next change sends it
        }
    }
}
