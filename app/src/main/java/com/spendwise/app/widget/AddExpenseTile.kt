package com.spendwise.app.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.net.toUri
import com.spendwise.app.MainActivity
import com.spendwise.app.ui.navigation.LINK_SCHEME

/** Quick Settings tile: opens Add expense, like the widget's +. On a locked phone it asks to unlock first. */
class AddExpenseTile : TileService() {
    override fun onClick() {
        if (isLocked) unlockAndRun(::open) else open()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the Intent form is the only one before Android 14
    private fun open() {
        val intent = Intent(Intent.ACTION_VIEW, "$LINK_SCHEME://add".toUri(), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, "tile".hashCode(), intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
