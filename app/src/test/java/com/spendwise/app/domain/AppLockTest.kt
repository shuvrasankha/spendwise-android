package com.spendwise.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    @Test fun `never hidden does not expire`() = assertFalse(lockExpired(null, 10_000_000))

    @Test fun `five minutes away is still open, a moment more locks`() {
        assertFalse(lockExpired(1_000, 1_000 + LOCK_AWAY_MS))
        assertTrue(lockExpired(1_000, 1_001 + LOCK_AWAY_MS))
    }
}
