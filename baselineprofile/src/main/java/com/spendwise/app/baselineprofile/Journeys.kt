package com.spendwise.app.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

const val PACKAGE = "com.spendwise.app"
private const val WAIT = 10_000L

/** The bottom bar shows only when signed in; signed out the app stays on the sign-in screen. */
fun MacrobenchmarkScope.signedIn(): Boolean = device.wait(Until.hasObject(By.text("History")), WAIT)

/** Dashboard is the start tab: wait for its content, then scroll to the bottom and back. */
fun MacrobenchmarkScope.dashboard() {
    device.wait(Until.hasObject(By.scrollable(true)), WAIT)
    device.waitForIdle()
    fling(Direction.DOWN, 3)
    fling(Direction.UP, 1)
}

/** History tab, then a few flings so rows, day headers and the next pages load. */
fun MacrobenchmarkScope.historyScroll() {
    device.findObject(By.text("History"))?.click() ?: return
    device.wait(Until.hasObject(By.scrollable(true)), WAIT)
    device.waitForIdle()
    fling(Direction.DOWN, 4)
}

/** Opens the New entry sheet (Expense) and types an amount. It never saves, so no data is written. */
fun MacrobenchmarkScope.addExpense() {
    device.findObject(By.desc("New entry"))?.click() ?: return
    device.wait(Until.hasObject(By.clazz("android.widget.EditText")), WAIT)
    device.findObject(By.clazz("android.widget.EditText"))?.text = "120"
    device.waitForIdle()
    device.pressBack() // keyboard
    device.pressBack() // sheet
    device.waitForIdle()
}

/** Finds the list again for every fling: it redraws as data and pages load, which makes an old handle stale. */
private fun MacrobenchmarkScope.fling(direction: Direction, times: Int) = repeat(times) {
    try {
        device.findObject(By.scrollable(true))?.run {
            setGestureMargin(device.displayWidth / 5)
            fling(direction)
        }
    } catch (_: StaleObjectException) {
        // Redrawn mid-gesture; the next fling finds it again
    }
    device.waitForIdle()
}
