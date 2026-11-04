package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TagSuggestionsTest {

    private fun expense(date: String, vararg tags: String) =
        Expense("id$date", 100.0, "food", date, "UPI", "", "", "", tags.toList())

    @Test fun mostUsedFirst_tiesToTheMostRecent() {
        val used = usedTags(
            listOf(
                expense("2026-10-01", "goa", "work"),
                expense("2026-10-05", "goa"),
                expense("2026-10-09", "wedding"),
                expense("2026-10-02", "work", "work"), // a repeat on one expense counts once
            ),
        )
        assertEquals(listOf("goa", "work", "wedding"), used)
    }

    @Test fun noTagsNoSuggestions() = assertEquals(emptyList<String>(), usedTags(listOf(expense("2026-10-01"))))

    @Test fun suggestions_skipChosenAndMatchTheDraft() {
        val used = listOf("goa", "Groceries", "work", "gym")
        assertEquals(listOf("goa", "Groceries", "work", "gym"), tagSuggestions(used, emptyList(), ""))
        assertEquals(listOf("Groceries", "work", "gym"), tagSuggestions(used, listOf("goa"), ""))
        assertEquals(listOf("goa", "Groceries", "gym"), tagSuggestions(used, emptyList(), "#G"))
        assertEquals(listOf("gym"), tagSuggestions(used, emptyList(), "gy"))
        assertEquals(emptyList<String>(), tagSuggestions(used, emptyList(), "zzz"))
    }

    @Test fun suggestions_capped() = assertEquals(2, tagSuggestions(listOf("a", "b", "c"), emptyList(), "", limit = 2).size)
}
