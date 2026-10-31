package com.spendwise.app.reminder

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.prettyPush
import com.spendwise.app.domain.pushIsDebt
import com.spendwise.app.domain.pushPage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Data messages from the Worker (docs/WORKER_FCM_SPEC.md): {kind: "reminder"} or {kind: "message", title, body, url, tag}. */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {
    @Inject lateinit var prefs: DevicePrefs
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["kind"]) {
            "reminder" -> Notifications.reminder(this)
            "message" -> {
                val title = data["title"].orEmpty().ifBlank { return }
                val tag = data["tag"]
                val channel = if (pushIsDebt(tag)) Notifications.DEBTS_CHANNEL else Notifications.BILLS_CHANNEL
                val (shownTitle, shownBody) = prettyPush(title, data["body"].orEmpty())
                Notifications.message(this, channel, tag, shownTitle, shownBody, pushPage(data["url"]))
            }
        }
    }

    /** The old token stops working: register the new one (the reminder falls back to this device if that fails). */
    override fun onNewToken(token: String) {
        scope.launch {
            if (prefs.pushEndpoint.first() == null) return@launch
            prefs.reminderAt.first()?.let { runCatching { scheduler.schedule(it) } }
        }
    }

    private companion object {
        // Outlives the service, so a registration started as it stops still finishes while the process lives
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
