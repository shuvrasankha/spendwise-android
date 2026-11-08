package com.spendwise.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.spendwise.app.data.worker.PushSync
import com.spendwise.app.reminder.BudgetAlertSync
import com.spendwise.app.reminder.ReminderSync
import com.spendwise.app.widget.TodayWidgetSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpendWiseApp : Application() {
    @Inject lateinit var reminderSync: ReminderSync
    @Inject lateinit var pushSync: PushSync
    @Inject lateinit var widgetSync: TodayWidgetSync
    @Inject lateinit var budgetAlerts: BudgetAlertSync

    override fun onCreate() {
        super.onCreate()
        reminderSync.start()
        pushSync.start()
        widgetSync.start()
        budgetAlerts.start()
        // Crash reports carry the uid only, so a tester's report can be matched; never amounts or names
        FirebaseAuth.getInstance().addAuthStateListener { FirebaseCrashlytics.getInstance().setUserId(it.currentUser?.uid ?: "") }
    }
}
