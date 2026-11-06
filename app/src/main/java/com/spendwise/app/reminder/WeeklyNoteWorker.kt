package com.spendwise.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.spendwise.app.domain.untilWeeklyNote
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Sunday evening's "Your week in money", while someone is signed in. Tapping it opens Ask, which writes the note. */
class WeeklyNoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser != null) Notifications.weeklyNote(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "weekly-note"

        /** shortcut: a 7-day periodic job can drift by up to its flex window; re-arm per run if exact Sunday timing matters. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyNoteWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(untilWeeklyNote(ZonedDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
