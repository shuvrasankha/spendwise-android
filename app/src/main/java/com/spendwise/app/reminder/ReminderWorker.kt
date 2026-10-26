package com.spendwise.app.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.MainActivity
import com.spendwise.app.R
import com.spendwise.app.domain.DEFAULT_REMIND_AT
import com.spendwise.app.domain.todayStr
import kotlinx.coroutines.tasks.await

/** "Log today's spending" (sw.js), unless something was already added today. Then sets up tomorrow's. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val at = inputData.getString(ReminderScheduler.KEY_AT) ?: DEFAULT_REMIND_AT
        ReminderScheduler.enqueue(applicationContext, at) // the next one first, so a failure below can't end the chain
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        if (!loggedToday(uid)) notifyNow()
        return Result.success()
    }

    /**
     * shortcut: the web's Worker skips the reminder once anything was added that day on any device; here an
     * expense or income dated today counts. Upgrade if debts or back-dated entries should count too.
     */
    private suspend fun loggedToday(uid: String): Boolean = try {
        val db = FirebaseFirestore.getInstance()
        val today = todayStr()
        listOf("expenses", "income").any { name ->
            !db.collection(name).whereEqualTo("uid", uid).whereEqualTo("date", today).limit(1).get().await().isEmpty
        }
    } catch (e: Exception) {
        false // can't tell: remind, as the web does when it hasn't heard otherwise
    }

    @SuppressLint("MissingPermission") // checked just below; denied means no notification, as on the web
    private fun notifyNow() {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        ReminderScheduler.createChannel(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_add)
            .setContentTitle("Log today's spending")
            .setContentText("Add what you spent today. It only takes a few seconds.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification) // one id: a newer reminder replaces an unread one
        } catch (e: SecurityException) {
            // permission withdrawn between the check and here
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
