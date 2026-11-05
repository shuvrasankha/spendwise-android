package com.spendwise.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold start, Dashboard, History scroll and Add expense. Run with `./gradlew :app:generateBaselineProfile`. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        if (!signedIn()) return@collect
        dashboard()
        historyScroll()
        addExpense()
    }
}
