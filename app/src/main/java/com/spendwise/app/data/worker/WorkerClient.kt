package com.spendwise.app.data.worker

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** config/ai.js › AI_PROXY_URL: the Cloudflare Worker for AI and push (spec §7.1). */
const val WORKER_URL = "https://spendwise-ai.spendwise-ai.workers.dev"
private const val TIMEOUT_MS = 30_000

class NotSignedIn : Exception("NOT_AUTHENTICATED")

data class WorkerResponse(val status: Int, val body: String) {
    val ok get() = status in 200..299
}

/**
 * callWorker: a JSON POST carrying the user's Firebase ID token. Callers map the status to their own
 * wording, as the web's do. Never send descriptions, notes or names to the AI paths.
 */
@Singleton
class WorkerClient @Inject constructor(private val auth: FirebaseAuth) {
    suspend fun post(path: String, body: JsonObject): WorkerResponse {
        val user = auth.currentUser ?: throw NotSignedIn()
        val token = user.getIdToken(false).await().token ?: throw NotSignedIn()
        return withContext(Dispatchers.IO) {
            val c = URL(WORKER_URL + path).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.doOutput = true
                c.setRequestProperty("Authorization", "Bearer $token")
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val status = c.responseCode
                val text = (if (status < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                WorkerResponse(status, text)
            } finally {
                c.disconnect()
            }
        }
    }
}
