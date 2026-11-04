package com.spendwise.app.ui.navigation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.spendwise.app.data.update.GithubUpdater
import kotlinx.coroutines.launch

/**
 * For installs outside Play: offers a newer GitHub release once per start, with × to put it off until the next
 * start (Settings › Check for updates offers it again any time). Play installs are left to [PlayUpdates].
 */
@Composable
fun GithubUpdates(snackbar: SnackbarHostState, updater: GithubUpdater) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (updater.fromPlay) return@LaunchedEffect
        // Offline or rate-limited: no update to offer
        val release = runCatching { updater.newerRelease() }.getOrNull() ?: return@LaunchedEffect
        val answer = snackbar.showSnackbar(
            "A new version is available", "Update", withDismissAction = true, duration = SnackbarDuration.Indefinite,
        )
        if (answer != SnackbarResult.ActionPerformed) return@LaunchedEffect
        if (!updater.canInstall) {
            updater.openAllowInstalls()
            snackbar.showSnackbar("Allow SpendWise to install updates, then check again in Settings")
            return@LaunchedEffect
        }
        scope.launch {
            runCatching { updater.install(release) }.onFailure { snackbar.showSnackbar("Couldn't download the update") }
        }
    }
}
