package com.spendwise.app.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Export files live in the app's cache and are shared through FileProvider, so no storage permission is needed. */
@Singleton
class ExportFiles @Inject constructor(@param:ApplicationContext private val context: Context) {
    /** Writes [content] (UTF-8, as given) to cache/exports/[name], replacing earlier exports, and returns a shareable Uri. */
    suspend fun write(name: String, content: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, name).apply { writeText(content, Charsets.UTF_8) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
