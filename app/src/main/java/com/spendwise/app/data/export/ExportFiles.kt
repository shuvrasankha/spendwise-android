package com.spendwise.app.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Files the user exports or picks. Shared exports live in the app's cache and are shared through FileProvider, so no storage permission is needed. */
@Singleton
class ExportFiles @Inject constructor(@param:ApplicationContext private val context: Context) {
    /** Writes [content] (UTF-8, as given) to cache/exports/[name], replacing earlier exports, and returns a shareable Uri. */
    suspend fun write(name: String, content: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, name).apply { writeText(content, Charsets.UTF_8) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Writes [content] (UTF-8) to a document the user picked (CreateDocument). */
    suspend fun save(uri: Uri, content: String) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Can't open $uri" }.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    /** Reads a document the user picked (OpenDocument) as UTF-8 text. */
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openInputStream(uri)) { "Can't open $uri" }.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** The picked document's file name, for showing what will be restored. */
    suspend fun name(uri: Uri): String? = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
}
