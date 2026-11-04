package com.spendwise.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.spendwise.app.domain.isNewerVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val LATEST = "https://api.github.com/repos/shuvrasankha/spendwise-android/releases/latest"
private const val PLAY = "com.android.vending"

class Release(val version: String, val apkUrl: String)

/**
 * Updates for installs outside Play (the APK on GitHub Releases): finds a newer release, downloads it and hands it
 * to the system installer, which replaces this install in place and keeps its data. Play installs update through Play.
 */
@Singleton
class GithubUpdater @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val pm get() = context.packageManager

    val fromPlay: Boolean
        get() {
            val installer = if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(context.packageName).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
            return installer == PLAY
        }

    val installedVersion: String get() = pm.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** The latest release if it's newer than this install, else null. Throws when GitHub can't be reached. */
    suspend fun newerRelease(): Release? = latestRelease()?.takeIf { isNewerVersion(it.version, installedVersion) }

    /** Android asks once per app before it installs an APK from here. */
    val canInstall: Boolean get() = pm.canRequestPackageInstalls()

    /** Opens the system page where the user allows SpendWise to install updates. */
    fun openAllowInstalls() = context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /** Downloads [release] and opens the system installer. Throws when the download fails. */
    suspend fun install(release: Release) {
        val apk = File(context.cacheDir, "updates/SpendWise.apk")
        download(release.apkUrl, apk)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Opens the app's page in the Play Store, where a Play install updates. */
    fun openPlayListing() = context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).setPackage(PLAY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun get(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    private suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
        val c = get(LATEST)
        try {
            if (c.responseCode != 200) throw IllegalStateException("GitHub ${c.responseCode}")
            val json = Json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject
            val apk = json["assets"]!!.jsonArray.map { it.jsonObject }
                .firstOrNull { it["name"]!!.jsonPrimitive.content.endsWith(".apk") } ?: return@withContext null
            Release(json["tag_name"]!!.jsonPrimitive.content, apk["browser_download_url"]!!.jsonPrimitive.content)
        } finally {
            c.disconnect()
        }
    }

    private suspend fun download(url: String, to: File) = withContext(Dispatchers.IO) {
        to.parentFile?.mkdirs()
        val c = get(url)
        try {
            c.inputStream.use { i -> to.outputStream().use { i.copyTo(it) } }
        } finally {
            c.disconnect()
        }
    }
}
