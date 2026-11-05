package com.spendwise.app.data.ai

import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.di.NoBackupDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/** Every provider a key can be saved for. */
val BYOK_PROVIDERS = listOf("nvidia")

/** The saved key couldn't be decrypted (Keystore reset, restore to a new phone); it has been removed. */
class KeyUnreadableException : Exception("KEY_UNREADABLE")

/**
 * The user's API keys, one file each in noBackupFilesDir, encrypted by [SecretCipher]. Only the last
 * 4 characters go to DataStore, for "Key saved ••••1a2b". Never log a key.
 */
@Singleton
class ApiKeyStore @Inject constructor(
    private val cipher: SecretCipher,
    @param:NoBackupDir private val dir: File,
    private val prefs: DevicePrefs,
) {
    private fun file(provider: String) = File(dir, "byok_$provider.bin")

    /** [key] is cleared before this returns. */
    suspend fun save(provider: String, key: CharArray) {
        try {
            require(key.size >= 8 && key.none { it.isWhitespace() }) { "not an API key" }
            val mask = String(key, key.size - 4, 4)
            withContext(Dispatchers.IO) {
                val buf = Charsets.UTF_8.encode(CharBuffer.wrap(key))
                val plain = ByteArray(buf.remaining()).also { buf.get(it) }
                buf.array().fill(0)
                try {
                    val tmp = File(dir, "byok_$provider.tmp")
                    tmp.writeBytes(cipher.encrypt(plain))
                    Files.move(tmp.toPath(), file(provider).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    plain.fill(0)
                }
            }
            prefs.setByokKeyMask(provider, mask)
        } finally {
            key.fill('\u0000')
        }
    }

    /** The key, or null when none is saved. Throws [KeyUnreadableException] after removing a key it can't decrypt. */
    suspend fun read(provider: String): String? {
        val f = file(provider)
        val plain = try {
            withContext(Dispatchers.IO) { if (f.exists()) cipher.decrypt(f.readBytes()) else null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            remove(provider)
            throw KeyUnreadableException()
        } ?: return null
        return try { String(plain, Charsets.UTF_8) } finally { plain.fill(0) }
    }

    suspend fun remove(provider: String) {
        withContext(Dispatchers.IO) {
            file(provider).delete()
            if (dir.listFiles { f -> f.name.startsWith("byok_") && f.name.endsWith(".bin") }.isNullOrEmpty()) runCatching { cipher.destroy() }
        }
        prefs.setByokKeyMask(provider, null)
    }

    /**
     * Log-out and account deletion: the key and the consent belong to the person, not the phone, so
     * the next account here starts on SpendWise AI and never sends its data to someone else's key.
     */
    suspend fun forgetAll() {
        prefs.setAiMode("worker")
        BYOK_PROVIDERS.forEach { remove(it); prefs.setByokConsent(it, false) }
    }

    /** From DataStore, never from decrypting. */
    fun hasKey(provider: String): Flow<Boolean> = prefs.byokKeyMask(provider).map { it != null }

    fun mask(provider: String): Flow<String?> = prefs.byokKeyMask(provider)
}
