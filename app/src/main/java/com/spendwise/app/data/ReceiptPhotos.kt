package com.spendwise.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val RECEIPT_MAX_SIDE = 1280

/** Receipt photos: the camera writes into the app's cache, and the photo is deleted once read. */
@Singleton
class ReceiptPhotos @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val dir get() = File(context.cacheDir, "receipts")

    /** Where the camera saves the next photo (TakePicture). */
    fun newUri(): Uri {
        val file = File(dir.apply { mkdirs() }, "receipt-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Removes every photo taken: none is kept after it was read. */
    fun clear() {
        dir.deleteRecursively()
    }

    /** receiptDataUrl: shrunk to at most 1280px on its long side, as a JPEG (quality 80) data URL. */
    suspend fun dataUrl(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decode(uri)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun size(w: Int, h: Int): Pair<Int, Int> {
        val scale = min(1.0, RECEIPT_MAX_SIDE.toDouble() / max(w, h))
        return (w * scale).roundToInt() to (h * scale).roundToInt()
    }

    private fun decode(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder turns the photo upright from its EXIF orientation, as imageOrientation: 'from-image' does
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = size(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // shortcut: Android 8 and 8.1 ignore the EXIF orientation, so a sideways photo is sent sideways; the AI still reads most of them
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= RECEIPT_MAX_SIDE) sample *= 2
        val raw = checkNotNull(context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }) { "Not an image" }
        val (w, h) = size(raw.width, raw.height)
        return if (w == raw.width && h == raw.height) raw else Bitmap.createScaledBitmap(raw, w, h, true).also { raw.recycle() }
    }
}
