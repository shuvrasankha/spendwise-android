package com.spendwise.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The header's status wording (offline-queue.js › updatePendingBadge, network-status.js). */
class SyncLineTest {
    @Test fun offline() {
        assertEquals("You're offline.", syncLine(online = false, pending = 0, backOnline = false))
        assertEquals("You're offline. 2 pending", syncLine(online = false, pending = 2, backOnline = false))
        assertEquals("You're offline. 99+ pending", syncLine(online = false, pending = 120, backOnline = false))
    }

    @Test fun online() {
        assertNull(syncLine(online = true, pending = 0, backOnline = false))
        assertEquals("3 pending", syncLine(online = true, pending = 3, backOnline = false))
        assertEquals("Back online! Syncing your changes...", syncLine(online = true, pending = 0, backOnline = true))
    }
}
