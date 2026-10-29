package com.spendwise.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST = "https://api.github.com/repos/shuvrasankha/spendwise-android/releases/latest"
private const val PLAY = "com.android.vending"

private class Release(val version: String, val apkUrl: String)

/** "v1.2-abc123" → [1, 2]; a tag with no leading number reads as no update. */
private fun parts(v: String) = Regex("""^v?(\d+(?:\.\d+)*)""").find(v)?.groupValues?.get(1)?.split('.')?.map { it.toInt() }

private fun isNewer(tag: String, installed: String): Boolean {
    val a = parts(tag) ?: return false
    val b = parts(installed) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

private fun get(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 15_000
    readTimeout = 30_000
    setRequestProperty("Accept", "application/vnd.github+json")
}

private suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
    val c = get(LATEST)
    try {
        if (c.responseCode != 200) return@withContext null
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

/**
 * For installs outside Play (the APK on GitHub Releases): offers a newer release once per start, downloads
 * it and hands it to the system installer, which replaces this install in place and keeps its data.
 * Play installs are left to [PlayUpdates].
 */
@Composable
fun GithubUpdates(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(context.packageName).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
        if (installer == PLAY) return@LaunchedEffect
        val installed = pm.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        // Offline or rate-limited: no update to offer
        val release = runCatching { latestRelease() }.getOrNull()?.takeIf { isNewer(it.version, installed) } ?: return@LaunchedEffect
        if (snackbar.showSnackbar("A new version is available", "Update", duration = SnackbarDuration.Indefinite) != SnackbarResult.ActionPerformed) return@LaunchedEffect
        if (!pm.canRequestPackageInstalls()) {
            snackbar.showSnackbar("Allow SpendWise to install updates, then tap Update again")
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return@LaunchedEffect
        }
        scope.launch {
            val apk = File(context.cacheDir, "updates/SpendWise.apk")
            runCatching { download(release.apkUrl, apk) }
                .onFailure { snackbar.showSnackbar("Couldn't download the update"); return@launch }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
