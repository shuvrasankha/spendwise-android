package com.spendwise.app.domain

/** Back after this long in the background: lock again (app-lock.js › AWAY_MS). */
const val LOCK_AWAY_MS = 5 * 60 * 1000L

/** app-lock.js › shouldLock, the away part: [awayAt] is when the app was last hidden, or null. */
fun lockExpired(awayAt: Long?, now: Long): Boolean = awayAt != null && now - awayAt > LOCK_AWAY_MS

// ── The app's own PIN (Android only, device only) ──

/** 4 to 6 digits. */
fun validPin(pin: String) = pin.length in 4..6 && pin.all { it in '0'..'9' }

private const val PIN_ROUNDS = 120_000

private fun pinHash(pin: String, salt: ByteArray): ByteArray =
    javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, PIN_ROUNDS, 256)).encoded

/** What's stored for [pin]: "salt:hash", both Base64. The PIN itself is never stored. */
fun pinRecord(pin: String, salt: ByteArray): String {
    val b64 = java.util.Base64.getEncoder()
    return b64.encodeToString(salt) + ":" + b64.encodeToString(pinHash(pin, salt))
}

/** Constant-time check of [pin] against a [pinRecord]; false for a damaged record. */
fun pinMatches(pin: String, record: String): Boolean = runCatching {
    val (salt, hash) = record.split(':').map { java.util.Base64.getDecoder().decode(it) }
    java.security.MessageDigest.isEqual(pinHash(pin, salt), hash)
}.getOrDefault(false)

/** Wrong PINs so far, and when the last one was typed: [at] in elapsed-since-boot ms, on boot number [boot]. */
data class PinFails(val count: Int = 0, val at: Long? = null, val boot: Int? = null)

const val PIN_FREE_TRIES = 5

/** The wait after [count] wrong PINs: none for the first 5, then 30 s, doubling, up to 15 min. */
fun pinWaitMs(count: Int): Long =
    if (count < PIN_FREE_TRIES) 0 else minOf(30_000L shl (count - PIN_FREE_TRIES).coerceAtMost(5), 15 * 60_000L)

/**
 * How long until another try, in ms. Elapsed time starts over at each boot, so on a different [boot] the whole
 * wait applies again (the caller stamps the new boot), which is why changing the clock or restarting can't skip it.
 */
fun pinWaitLeft(f: PinFails, now: Long, boot: Int): Long {
    val wait = pinWaitMs(f.count)
    if (wait == 0L || f.at == null) return 0
    if (f.boot != boot) return wait
    return (f.at + wait - now).coerceIn(0, wait)
}
