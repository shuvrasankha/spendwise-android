package com.spendwise.app.data.worker

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.todayStr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

fun loggedBody(date: String): JsonObject = buildJsonObject { put("date", date) }
fun debtPushBody(debtId: String, symbol: String): JsonObject = buildJsonObject { put("debtId", debtId); put("symbol", symbol) }

/**
 * The Worker calls the web makes after a save (reminders.js › markLoggedToday, split.js › announceDebts,
 * claimSettled). Best effort: logged, never shown. Repositories call these once the write reaches the
 * server, so the Worker can read the document it's told about.
 */
@Singleton
class WorkerPush @Inject constructor(private val client: WorkerClient, private val prefs: DevicePrefs, private val auth: FirebaseAuth) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Today's "Log today's spending" is done on every device; sent once a day per user, until it succeeds. */
    fun logged() = fire {
        val uid = auth.currentUser?.uid ?: return@fire
        val key = "$uid:${todayStr()}"
        if (prefs.loggedToday.first() == key) return@fire
        if (client.post("/push/logged", loggedBody(todayStr())).ok) prefs.setLoggedToday(key)
    }

    /** A debt shared by email: the Worker checks it's ours and new, and pushes the other person once. */
    fun debt(id: String) = fire { client.post("/push/debt", debtPushBody(id, prefs.currency.first().symbol)) }

    /** The other person says it's paid: the one who added it is pushed. */
    fun claim(id: String) = fire { client.post("/push/claim", debtPushBody(id, prefs.currency.first().symbol)) }

    private fun fire(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WorkerPush", "Worker call failed", e)
            }
        }
    }
}
