package com.spendwise.app.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HttpReply(val status: Int, val body: String) {
    val ok get() = status in 200..299
}

/** A JSON POST to an AI provider. Behind an interface so provider tests use a fake transport. */
interface AiHttp {
    suspend fun post(url: String, headers: Map<String, String>, body: String, readTimeoutMs: Int): HttpReply
}

private const val CONNECT_TIMEOUT_MS = 30_000

/** HttpURLConnection only (D1). Cancelling the call disconnects, so a closed screen stops the request. */
@Singleton
class UrlConnectionHttp @Inject constructor() : AiHttp {
    override suspend fun post(url: String, headers: Map<String, String>, body: String, readTimeoutMs: Int): HttpReply {
        val u = URL(url)
        require(u.protocol == "https") { "AI requests must use https" } // the key goes in a header
        return withContext(Dispatchers.IO) {
            val c = u.openConnection() as HttpURLConnection
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { c.disconnect() }
                try {
                    c.requestMethod = "POST"
                    c.connectTimeout = CONNECT_TIMEOUT_MS
                    c.readTimeout = readTimeoutMs
                    c.doOutput = true
                    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                    c.outputStream.use { it.write(body.toByteArray()) }
                    val status = c.responseCode
                    Log.d("AiHttp", "POST ${u.host}${u.path}: $status") // the host, path and status only, never headers or bodies
                    val text = (if (status < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    cont.resume(HttpReply(status, text))
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                } finally {
                    c.disconnect()
                }
            }
        }
    }
}
