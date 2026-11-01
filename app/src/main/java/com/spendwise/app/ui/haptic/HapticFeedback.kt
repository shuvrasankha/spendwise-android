package com.spendwise.app.ui.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide haptic feedback helper.
 *
 * Each effect has a meaningful name so call-sites express intent rather than raw vibration
 * durations.  All vibrations are no-ops when [enabled] is false.
 *
 * Tap-style effects use [HapticFeedbackConstants] on Android 13+ for the most faithful
 * system feel and fall back to [VibrationEffect.createOneShot] on older devices.
 */
@Singleton
class HapticManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** Toggled from [com.spendwise.app.data.prefs.DevicePrefs]. */
    var enabled: Boolean = true

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun canVibrate(): Boolean = enabled && vibrator?.hasVibrator() == true

    // ── Public effects ────────────────────────────────────────────────────────

    /** Bottom-bar tab switch, chip select — brief, neutral. */
    fun tick() = vibrate(ms = 12, amplitude = 40)

    /** Button press, toggle — slightly heavier than tick. */
    fun light() = vibrate(ms = 16, amplitude = 60)

    /** Confirmation actions — save, mark paid, log out. */
    fun confirm() = vibrate(ms = 22, amplitude = 90)

    /** Error / denied actions — validation fail, permission blocked. */
    fun error() {
        if (!canVibrate()) return
        // Two quick pulses signal rejection
        vibrate(ms = 18, amplitude = 80)
        android.os.SystemClock.sleep(60)
        vibrate(ms = 18, amplitude = 80)
    }

    /** FAB "New entry" press — slightly richer than light. */
    fun medium() = vibrate(ms = 20, amplitude = 75)

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun vibrate(ms: Long, amplitude: Int) {
        if (!canVibrate()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val safeAmplitude = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(ms, safeAmplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}

/** Convenience: let any composable call haptic effects without drilling the manager down. */
val LocalHaptic = staticCompositionLocalOf<HapticManager> {
    error("HapticManager not provided — wrap with CompositionLocalProvider(LocalHaptic provides …)")
}

/**
 * Wraps a click lambda so that [effect] fires before the original action.
 * Usage: `onClick = haptic.wrap(haptic::light) { doSomething() }`
 */
fun HapticManager.wrap(effect: () -> Unit, action: () -> Unit): () -> Unit = {
    effect()
    action()
}

/**
 * [View]-based haptic for non-Compose entry points (widgets, legacy views).
 * Uses [HapticFeedbackConstants.VIRTUAL_KEY] which respects the system haptic preference.
 */
fun View.performHapticIfEnabled(haptic: HapticManager) {
    if (haptic.enabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
