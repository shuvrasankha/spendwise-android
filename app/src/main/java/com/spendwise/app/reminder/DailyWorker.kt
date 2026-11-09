package com.spendwise.app.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestoreException
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.autoBills
import com.spendwise.app.domain.billTitle
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.format.formatWhole
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DailyWorkerEntryPoint {
    fun expenses(): ExpenseRepository
    fun settings(): SettingsRepository
    fun prefs(): DevicePrefs
    fun budgetAlerts(): BudgetAlertSync
}

/**
 * Once a day, online, while someone is signed in, even if the app isn't opened: adds the repeating bills that
 * are due (only when this account turned that on, on this device; domain › autoBills), then checks the budget
 * alerts, so spending added on the web alerts too. Everything is read from the server: a cached read could miss
 * a bill already added on the web and add it twice. Offline it waits for the network.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val deps = EntryPointAccessors.fromApplication(applicationContext, DailyWorkerEntryPoint::class.java)
        val prefs = deps.prefs()
        return try {
            withTimeout(TIMEOUT_MS) {
                var list = deps.expenses().fromServer()
                if (prefs.autoBills(uid).first()) {
                    val today = todayStr()
                    val skipped = prefs.skippedBills.first()
                    val bills = autoBills(uid, list, today, prefs.autoBillsThrough(uid).first()) { key, month -> "$key:$month" in skipped }
                    bills.forEach { deps.expenses().addAutoBill(it).confirmed() }
                    // Only once every bill is on the server, so a failed run tries the same months again
                    prefs.setAutoBillsThrough(uid, today.take(7))
                    if (bills.isNotEmpty()) {
                        val currency = prefs.currency.first()
                        val text = bills.joinToString(", ") { "${billTitle(it.template)} ${formatWhole(it.template.amount, currency)}" }
                        val title = if (bills.size == 1) "Added ${billTitle(bills[0].template)}" else "Added ${bills.size} repeating bills"
                        Notifications.message(applicationContext, Notifications.BILLS_CHANNEL, "auto-bills", title, text, "dashboard")
                        list = deps.expenses().fromServer()
                    }
                }
                deps.budgetAlerts().check(list, deps.settings().fromServer().budget)
            }
            Result.success()
        } catch (e: TimeoutCancellationException) {
            Result.retry() // slow network: the bill ids are fixed, so a write that did land is just written again
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                FirebaseCrashlytics.getInstance().recordException(e)
                Result.failure()
            } else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "daily"
        private const val NOW_NAME = "daily-now"
        private const val TIMEOUT_MS = 60_000L
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** From app start; KEEP, so starting the app again doesn't push the next run back. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Right after automatic bills are turned on, so this month's due ones don't wait a day. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailyWorker>().setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
