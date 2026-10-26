package com.spendwise.app.data

import com.spendwise.app.data.account.DeletionStep
import com.spendwise.app.data.account.MAX_BATCH
import com.spendwise.app.data.account.deletionBatches
import com.spendwise.app.data.account.deletionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** settings.remindOn / remindAt shapes, and the account deletion plan (spec §7.5, §8.2). */
class SettingsAndDeletionTest {

    @Test fun reminder_writesOneKeyEach_likeTheWeb() {
        assertEquals(mapOf("remindOn" to true), remindOnFields(true))
        assertEquals(mapOf("remindOn" to false), remindOnFields(false))
        assertEquals(mapOf("remindAt" to "07:05"), remindAtFields("07:05"))
    }

    @Test fun reminder_readTolerantly() {
        val s = settingsFrom(mapOf("remindOn" to true, "remindAt" to "20:30"))
        assertEquals(true to "20:30", s.remindOn to s.remindAt)
        val odd = settingsFrom(mapOf("remindOn" to "yes", "remindAt" to 2030))
        assertNull(odd.remindOn)
        assertNull(odd.remindAt)
        assertNull(settingsFrom(null).remindOn)
    }

    @Test fun deletionPlan_onlyTheUsersOwnDocs_settingsLast() {
        assertEquals(
            listOf(
                DeletionStep.Owned("expenses", "u1", "expenses"),
                DeletionStep.Owned("income", "u1", "income"),
                DeletionStep.Owned("debts", "u1", "debts"),
                DeletionStep.Owned("holdings", "u1", "investments"),
                DeletionStep.Doc("settings", "u1", "settings"),
            ),
            deletionPlan("u1"),
        )
    }

    @Test fun deletionPlan_neverQueriesSharedDebts() {
        // Shared debts are found by withEmail and belong to someone else: every query is on the user's own uid
        deletionPlan("u1").forEach {
            when (it) {
                is DeletionStep.Owned -> assertEquals("u1", it.uid)
                is DeletionStep.Doc -> assertEquals("settings" to "u1", it.collection to it.id)
            }
        }
    }

    @Test fun deletionBatches_atMost500() {
        assertEquals(500, MAX_BATCH)
        assertEquals(listOf(500, 500, 1), deletionBatches((1..1001).toList()).map { it.size })
        assertEquals(emptyList<List<Int>>(), deletionBatches(emptyList<Int>()))
        assertEquals(listOf(listOf("a", "b")), deletionBatches(listOf("a", "b")))
    }
}
