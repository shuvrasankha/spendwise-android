package com.spendwise.app

import android.app.Application
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.worker.PushSync
import com.spendwise.app.reminder.BudgetAlertSync
import com.spendwise.app.reminder.ReminderSync
import com.spendwise.app.ui.haptic.HapticManager
import com.spendwise.app.widget.TodayWidgetSync
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SpendWiseApp : Application() {
    @Inject lateinit var reminderSync: ReminderSync
    @Inject lateinit var pushSync: PushSync
    @Inject lateinit var widgetSync: TodayWidgetSync
    @Inject lateinit var budgetAlerts: BudgetAlertSync
    @Inject lateinit var prefs: DevicePrefs
    @Inject lateinit var haptic: HapticManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        reminderSync.start()
        pushSync.start()
        widgetSync.start()
        budgetAlerts.start()
        // Keep HapticManager in sync with the stored preference
        appScope.launch {
            haptic.enabled = prefs.hapticEnabled.first()
            prefs.hapticEnabled.collect { haptic.enabled = it }
        }
    }
}
