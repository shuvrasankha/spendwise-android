package com.spendwise.app.reminder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.spendwise.app.SpendWiseActivity
import com.spendwise.app.R
import com.spendwise.app.domain.REMINDER_TEXT
import com.spendwise.app.domain.REMINDER_TITLE
import com.spendwise.app.domain.WEEKLY_NOTE_TEXT
import com.spendwise.app.domain.WEEKLY_NOTE_TITLE
import com.spendwise.app.ui.navigation.LINK_SCHEME

/** The app's notifications: the daily reminder, and messages the Worker pushes (sw.js › push). */
object Notifications {
    const val REMINDER_CHANNEL = "daily-reminder"
    const val DEBTS_CHANNEL = "shared-debts"
    const val BILLS_CHANNEL = "bills"
    const val BUDGET_CHANNEL = "budget-alerts"
    const val WEEKLY_CHANNEL = "weekly-note"
    const val REVIEW_CHANNEL = "sms-review"
    private const val REVIEW_TAG = "sms-review"
    private const val REMINDER_ID = 1001
    private const val WEEKLY_ID = 1002

    /** Opens Add Expense, as the web's reminder opens expense.html. One id: a newer reminder replaces an unread one. */
    fun reminder(context: Context) = show(context, REMINDER_CHANNEL, null, REMINDER_ID, REMINDER_TITLE, REMINDER_TEXT, "add")

    /** Opens Ask on this week; the AI writes the note only when it's opened, so nothing is sent in the background. */
    fun weeklyNote(context: Context) = show(context, WEEKLY_CHANNEL, null, WEEKLY_ID, WEEKLY_NOTE_TITLE, WEEKLY_NOTE_TEXT, "week")

    /** Silent, one at a time, the count only: no amount on the lock screen. Opens Home, where they wait. */
    fun reviewWaiting(context: Context, count: Int) = message(
        context, REVIEW_CHANNEL, REVIEW_TAG,
        if (count == 1) "1 payment to review" else "$count payments to review",
        "Read from your bank SMS. Approve them in SpendWise.", "dashboard",
    )

    fun reviewDone(context: Context) = cancel(context, setOf(REVIEW_TAG))

    /** One per tag, so a re-sent alert replaces the earlier one, as the web's tag does. */
    fun message(context: Context, channel: String, tag: String?, title: String, body: String, page: String) =
        show(context, channel, tag, 0, title, body, page)

    /** notifications.js › closePushes: takes the tagged messages out of the tray. */
    fun cancel(context: Context, tags: Set<String>) {
        val manager = NotificationManagerCompat.from(context)
        tags.forEach { manager.cancel(it, 0) }
    }

    @SuppressLint("MissingPermission") // checked just below; denied means no notification, as on the web
    private fun show(context: Context, channel: String, tag: String?, id: Int, title: String, text: String, page: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannels(context)
        val open = PendingIntent.getActivity(
            context, (tag ?: page).hashCode(),
            Intent(Intent.ACTION_VIEW, "$LINK_SCHEME://$page".toUri(), context, SpendWiseActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_spendwise)
            .setColor(0xFFE0A11B.toInt()) // the accent, tinting the small icon
            .setCategory(if (channel == DEBTS_CHANNEL) NotificationCompat.CATEGORY_SOCIAL else NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(tag, id, notification)
        } catch (e: SecurityException) {
            // permission withdrawn between the check and here
        }
    }

    private fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(REMINDER_CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(DEBTS_CHANNEL, "Shared debts", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(BILLS_CHANNEL, "Bills and monthly summary", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(BUDGET_CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(WEEKLY_CHANNEL, "Weekly money note", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(REVIEW_CHANNEL, "Bank SMS to review", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }
}
