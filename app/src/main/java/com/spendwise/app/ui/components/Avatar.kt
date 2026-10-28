package com.spendwise.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The account photo (a Google sign-in's), else the first letter of the name or email, as the web's avatar
 * (page-shell.js › updateUserAvatar): the letter shows while the photo loads and if it fails.
 */
@Composable
fun Avatar(name: String, size: Dp = 40.dp, photoUrl: String = "") = Box(
    Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
) {
    Text(name.ifBlank { "U" }.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    val photo by produceState(cached?.takeIf { it.first == photoUrl }?.second, photoUrl) {
        if (value == null && photoUrl.isNotEmpty()) value = loadPhoto(photoUrl)?.also { cached = photoUrl to it }
    }
    photo?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

// The one signed-in photo, so the header and Settings don't fetch it again on every visit
private var cached: Pair<String, ImageBitmap>? = null

private suspend fun loadPhoto(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } finally {
            conn.disconnect()
        }
    }.getOrNull() // offline or a bad link: the letter stays, as the web's img error does
}
