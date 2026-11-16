package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    @Test fun `never hidden does not expire`() = assertFalse(lockExpired(null, 10_000_000))

    @Test fun `five minutes away is still open, a moment more locks`() {
        assertFalse(lockExpired(1_000, 1_000 + LOCK_AWAY_MS))
        assertTrue(lockExpired(1_000, 1_001 + LOCK_AWAY_MS))
    }

    @Test fun `pins are 4 to 6 digits`() {
        assertTrue(validPin("1234"))
        assertTrue(validPin("123456"))
        assertFalse(validPin("123"))
        assertFalse(validPin("1234567"))
        assertFalse(validPin("12a4"))
    }

    @Test fun `the record checks the pin and never holds it`() {
        val record = pinRecord("2468", ByteArray(16) { it.toByte() })
        assertFalse(record.contains("2468"))
        assertTrue(pinMatches("2468", record))
        assertFalse(pinMatches("2469", record))
        assertFalse(pinMatches("2468", "damaged"))
        assertNotEquals(record, pinRecord("2468", ByteArray(16) { 1 })) // salted
    }

    @Test fun `five free tries, then a doubling wait up to 15 minutes`() {
        assertEquals(0L, pinWaitMs(4))
        assertEquals(30_000L, pinWaitMs(5))
        assertEquals(60_000L, pinWaitMs(6))
        assertEquals(15 * 60_000L, pinWaitMs(40))
    }

    @Test fun `the wait counts down, and a restart starts it over`() {
        val f = PinFails(5, at = 1_000, boot = 7)
        assertEquals(20_000L, pinWaitLeft(f, 11_000, 7))
        assertEquals(0L, pinWaitLeft(f, 40_000, 7))
        assertEquals(30_000L, pinWaitLeft(f, 5, 8))
        assertEquals(0L, pinWaitLeft(PinFails(3, 1_000, 7), 1_001, 7))
    }
}
