package com.spendwise.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PLAY = "com.android.vending"

class Release(val version: String, val apkUrl: String)

/**
 * The Play build's updater: Play updates the app, so there's no download or install code here, and no
 * install permission. Same API as the GitHub build's, so callers don't change; [fromPlay] is always true.
 */
@Singleton
class GithubUpdater @Inject constructor(@param:ApplicationContext private val context: Context) {
    val fromPlay: Boolean = true

    val installedVersion: String get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    suspend fun newerRelease(): Release? = null

    val canInstall: Boolean = false

    fun openAllowInstalls() = Unit

    suspend fun install(release: Release): Unit = throw UnsupportedOperationException("Play installs update through Play")

    /** Opens the app's page in the Play Store, where a Play install updates. */
    fun openPlayListing() = context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).setPackage(PLAY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
