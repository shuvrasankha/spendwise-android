package com.spendwise.app

import android.app.Application
import com.spendwise.app.data.worker.PushSync
import com.spendwise.app.reminder.ReminderSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpendWiseApp : Application() {
    @Inject lateinit var reminderSync: ReminderSync
    @Inject lateinit var pushSync: PushSync

    override fun onCreate() {
        super.onCreate()
        reminderSync.start()
        pushSync.start()
    }
}
