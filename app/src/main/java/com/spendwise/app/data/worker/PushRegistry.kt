package com.spendwise.app.data.worker

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.spendwise.app.data.prefs.DevicePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val FCM_PREFIX = "fcm:"
val PUSH_SYNC_PATHS = listOf("/push/bills", "/push/months")

fun subscribeBody(endpoint: String, remindAt: String, tz: String): JsonObject =
    buildJsonObject { put("endpoint", endpoint); put("remindAt", remindAt); put("tz", tz) }

fun endpointBody(endpoint: String): JsonObject = buildJsonObject { put("endpoint", endpoint) }

/**
 * This device on the Worker as "fcm:<token>" (reminders.js › turnOn, turnOff), so its cron sends the daily
 * reminder, bill alerts, month summaries and shared-debt messages here. Native push is the Worker change in
 * docs/WORKER_FCM_SPEC.md.
 */
@Singleton
class PushRegistry @Inject constructor(private val client: WorkerClient, private val prefs: DevicePrefs) {

    /**
     * Registers with the reminder time. True while the Worker sends this device's reminder: it took this one, or a
     * failed retry left the one it already had (at the old time, until the next try). False: use the local reminder.
     */
    suspend fun subscribe(remindAt: String): Boolean {
        val before = prefs.pushEndpoint.first()
        return try {
            val endpoint = FCM_PREFIX + FirebaseMessaging.getInstance().token.await()
            before?.takeIf { it != endpoint }?.let { old -> runCatching { client.post("/push/unsubscribe", endpointBody(old)) } }
            if (client.post("/push/subscribe", subscribeBody(endpoint, remindAt, ZoneId.systemDefault().id)).ok) {
                prefs.setPushEndpoint(endpoint, remindAt)
                // What was sent before this device counted may not have been kept (reminders.js › resendSync)
                PUSH_SYNC_PATHS.forEach { prefs.setPushSynced(it, null) }
                true
            } else {
                if (before != endpoint) prefs.setPushEndpoint(null, null)
                before == endpoint
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PushRegistry", "Subscribe failed", e)
            before != null // offline: whatever the Worker had, it still has
        }
    }

    /** Stops the Worker's pushes here. Best effort: the endpoint is forgotten even if the Worker can't be reached. */
    suspend fun unsubscribe() {
        val endpoint = prefs.pushEndpoint.first() ?: return
        prefs.setPushEndpoint(null, null)
        runCatching { withTimeoutOrNull(TIMEOUT_MS) { client.post("/push/unsubscribe", endpointBody(endpoint)) } }
    }

    /** Before signing out or deleting the account: unsubscribe, and drop the token so a missed unsubscribe can't deliver. */
    suspend fun unregister() {
        unsubscribe()
        runCatching { withTimeoutOrNull(TIMEOUT_MS) { FirebaseMessaging.getInstance().deleteToken().await() } }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
