package com.spendwise.app.domain

/** Back after this long in the background: lock again (app-lock.js › AWAY_MS). */
const val LOCK_AWAY_MS = 5 * 60 * 1000L

/** app-lock.js › shouldLock, the away part: [awayAt] is when the app was last hidden, or null. */
fun lockExpired(awayAt: Long?, now: Long): Boolean = awayAt != null && now - awayAt > LOCK_AWAY_MS
