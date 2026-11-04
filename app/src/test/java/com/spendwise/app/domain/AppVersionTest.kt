package com.spendwise.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test fun newerByAnyPart() {
        assertTrue(isNewerVersion("v1.6.5", "1.6.1"))
        assertTrue(isNewerVersion("v1.7", "1.6.9"))
        assertTrue(isNewerVersion("1.6.10", "1.6.9"))
        assertTrue(isNewerVersion("v1.6.1", "1.6"))
    }

    @Test fun sameOrOlderIsNoUpdate() {
        assertFalse(isNewerVersion("v1.6.1", "1.6.1"))
        assertFalse(isNewerVersion("v1.6", "1.6.0"))
        assertFalse(isNewerVersion("v1.5.9", "1.6"))
    }

    @Test fun suffixesIgnoredAndJunkIsNoUpdate() {
        assertTrue(isNewerVersion("v1.7-beta", "1.6.5"))
        assertFalse(isNewerVersion("nightly", "1.6.5"))
        assertFalse(isNewerVersion("v1.7", ""))
    }
}
