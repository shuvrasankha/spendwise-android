package com.spendwise.app.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.worker.PushRegistry
import com.spendwise.app.domain.untilNextReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The daily reminder: sent by the Worker over FCM when it has this device, else one unique WorkManager job for the next HH:mm, which schedules the one
 * after when it runs. WorkManager keeps it across reboots and app updates.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: DevicePrefs,
    private val push: PushRegistry,
) {
    /** Notifications are allowed: POST_NOTIFICATIONS on Android 13+, and not switched off for the app. */
    fun permitted(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** From the Worker when it takes this device (reminders.js › turnOn), else this device's own job. */
    suspend fun schedule(at: String) {
        prefs.setReminderAt(at)
        if (push.subscribe(at)) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) else enqueue(context, at)
    }

    suspend fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        push.unsubscribe()
        prefs.setReminderAt(null)
    }

    companion object {
        const val WORK_NAME = "daily-reminder"
        const val KEY_AT = "at"

        /** Replaces any pending one, so changing the time never leaves two. */
        fun enqueue(context: Context, at: String) {
            val delay = untilNextReminder(ZonedDateTime.now(), at)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_AT to at))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
