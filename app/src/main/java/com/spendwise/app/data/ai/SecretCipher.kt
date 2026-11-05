package com.spendwise.app.data.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypts the user's API key. Behind an interface because the Keystore doesn't run in JVM tests. */
interface SecretCipher {
    /** Returns version + IV + ciphertext. */
    fun encrypt(plain: ByteArray): ByteArray
    /** Throws on any failure: a wrong version, a lost Keystore key, a tampered blob. */
    fun decrypt(blob: ByteArray): ByteArray
    /** Deletes the Keystore entry. */
    fun destroy()
}

private const val ALIAS = "spendwise_byok_v1"
private const val VERSION: Byte = 1
private const val IV_BYTES = 12
private const val TAG_BITS = 128

/** AES-256-GCM with a key that never leaves the Android Keystore; no user authentication, so AI works with the app lock off. */
@Singleton
class KeystoreCipher @Inject constructor() : SecretCipher {
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    private fun newKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: newKey()) // the cipher picks a fresh random IV
        return byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 1 + IV_BYTES && blob[0] == VERSION) { "unknown key blob" }
        val key = existingKey() ?: error("no keystore key")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 1, IV_BYTES))
        return cipher.doFinal(blob, 1 + IV_BYTES, blob.size - 1 - IV_BYTES)
    }

    override fun destroy() {
        keyStore().deleteEntry(ALIAS)
    }
}
