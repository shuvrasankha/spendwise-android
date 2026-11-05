package com.spendwise.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.spendwise.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launcher icons a user can pick. Each is an `<activity-alias>` in the manifest; [alias] is its name.
 * [tile] and [mark] draw the preview in Settings. Only the colour pairs with enough contrast at launcher size.
 */
enum class AppIcon(val alias: String, val label: String, val tile: Long, @param:DrawableRes val mark: Int) {
    DARK(".MainActivity", "Dark", 0xFF1C1C1E, R.drawable.ic_launcher_foreground),
    LIGHT(".IconLight", "Light", 0xFFFAF8F5, R.drawable.ic_launcher_foreground_dark),
    ORANGE(".IconOrange", "Orange", 0xFFFF9F0A, R.drawable.ic_launcher_foreground_dark),
}

/**
 * Switches the launcher icon by enabling one alias and disabling the rest. PackageManager holds the choice,
 * so there's nothing else to store, and it's per device like the other display settings.
 *
 * Disabling the alias the open task was started from closes that task, so a pick waits in [pending] and
 * [applyPending] runs when the app leaves the screen (SpendWiseActivity.onStop).
 */
@Singleton
class AppIconSwitcher @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile private var pending: AppIcon? = null

    private fun component(icon: AppIcon) = ComponentName(context.packageName, context.packageName + icon.alias)

    /** The pick waiting to apply, else the enabled alias ("default" state is the manifest's: only DARK). */
    fun current(): AppIcon = pending ?: run {
        val pm = context.packageManager
        AppIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.DARK
                else -> false
            }
        } ?: AppIcon.DARK
    }

    fun set(icon: AppIcon) {
        pending = icon
    }

    /** Enables the pick before disabling the others, so there's never a moment with no launcher entry. */
    fun applyPending() {
        val icon = pending ?: return
        pending = null
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
