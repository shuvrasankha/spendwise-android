package com.spendwise.app.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.tasks.await

/**
 * Play's flexible in-app update: when a newer version is live on Play, offers it once per start, downloads it in
 * the background, then "Restart" installs it. Installs from anywhere but Play see nothing.
 */
@Composable
fun PlayUpdates(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val manager = remember { AppUpdateManagerFactory.create(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    var offered by rememberSaveable { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(manager) {
        val listener = InstallStateUpdatedListener { if (it.installStatus() == InstallStatus.DOWNLOADED) ready = true }
        manager.registerListener(listener)
        onDispose { manager.unregisterListener(listener) }
    }
    LaunchedEffect(manager) {
        // Not from Play, offline, or no Play Store: no update to offer
        val info = runCatching { manager.appUpdateInfo.await() }.getOrNull() ?: return@LaunchedEffect
        when {
            info.installStatus() == InstallStatus.DOWNLOADED -> ready = true
            !offered && info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.installStatus() == InstallStatus.UNKNOWN && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                offered = true
                manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
            }
        }
    }
    LaunchedEffect(ready) {
        if (ready && snackbar.showSnackbar("An update is ready", "Restart", duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) {
            manager.completeUpdate()
        }
    }
}
