package com.spendwise.app.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedChatsTest {
    private fun chat(id: Long, q: String = "q$id") = SavedChat(id, id, listOf(AssistantTurn(TurnKind.USER, q)))

    @Test fun anUpdatedChatReplacesItselfAndMovesToTheTop() {
        val list = upsertChat(listOf(chat(2), chat(1)), chat(1, "again"))
        assertEquals(listOf(1L, 2L), list.map { it.id })
        assertEquals("again", list.first().title)
    }

    @Test fun onlyTheNewestAreKept() {
        val full = (CHATS_KEPT downTo 1).map { chat(it.toLong()) }
        val list = upsertChat(full, chat(100))
        assertEquals(CHATS_KEPT, list.size)
        assertEquals(100L, list.first().id)
        assertEquals(2L, list.last().id)
    }

    @Test fun renamingKeepsTheChatsPlaceAndBlankGoesBackToTheQuestion() {
        val named = renameChat(listOf(chat(2), chat(1)), 1, "  Rent check  ")
        assertEquals(listOf(2L, 1L), named.map { it.id })
        assertEquals("Rent check", named[1].title)
        assertEquals("q2", named[0].title)
        assertEquals("q1", renameChat(named, 1, " ")[1].title)
        assertEquals(CHAT_NAME_MAX, renameChat(named, 1, "x".repeat(100))[1].title.length)
    }
}
