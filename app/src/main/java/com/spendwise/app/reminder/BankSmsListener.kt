package com.spendwise.app.reminder

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.di.AppScope
import com.spendwise.app.domain.SmsEntry
import com.spendwise.app.domain.parseSms
import com.spendwise.app.domain.queued
import com.spendwise.app.domain.reviewItem
import com.spendwise.app.domain.smsFingerprint
import com.spendwise.app.ui.navigation.SHARE_LIMIT
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reads bank SMS from the default SMS app's notifications into the review list (domain › AutoLog). Only that one
 * app; everything else, and any message parseSms doesn't read as a payment, is ignored and never kept. Nothing is
 * saved to the account here: each payment waits for approval in the app.
 */
@AndroidEntryPoint
class BankSmsListener : NotificationListenerService() {
    @Inject lateinit var prefs: DevicePrefs
    @Inject @AppScope lateinit var scope: CoroutineScope

    /**
     * Android can drop calls while it (re)connects the listener: after an app update, a reboot, or turning access
     * on. Messages already in the tray are read again then; the seen list keeps each one from being added twice.
     */
    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach(::onNotificationPosted)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.packageName != Telephony.Sms.getDefaultSmsPackage(this)) return
        val messages = messagesOf(n).takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            if (FirebaseAuth.getInstance().currentUser == null || !prefs.smsLog.first()) return@launch
            val today = LocalDate.now()
            var added = false
            for ((text, at) in messages) {
                val spent = parseSms(text.take(SHARE_LIMIT), today) as? SmsEntry.Spent ?: continue
                val id = smsFingerprint(text, at)
                if (!prefs.markSmsSeen(id)) continue
                prefs.updateSmsReview { queued(it, reviewItem(id, spent)) }
                added = true
            }
            if (added) Notifications.reviewWaiting(this@BankSmsListener, prefs.smsReview.first().size)
        }
    }

    /** Each message with its time. A conversation lists its recent messages; one sent by this phone has no sender. */
    private fun messagesOf(n: Notification): List<Pair<String, Long>> {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style != null) return style.messages.filter { it.person != null }.mapNotNull { m -> m.text?.toString()?.let { it to m.timestamp } }
        val text = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)
        return listOfNotNull(text?.toString()?.let { it to n.`when` })
    }
}
