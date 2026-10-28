package com.spendwise.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.domain.DEFAULT_REMIND_AT
import com.spendwise.app.domain.todayStr
import kotlinx.coroutines.tasks.await

/** "Log today's spending" (sw.js), unless something was already added today. Then sets up tomorrow's. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val at = inputData.getString(ReminderScheduler.KEY_AT) ?: DEFAULT_REMIND_AT
        ReminderScheduler.enqueue(applicationContext, at) // the next one first, so a failure below can't end the chain
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        if (!loggedToday(uid)) Notifications.reminder(applicationContext)
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
}
