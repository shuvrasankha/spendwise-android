package com.spendwise.app.ui.navigation

import com.spendwise.app.domain.HistoryRange
import com.spendwise.app.ui.history.HistoryTab
import com.spendwise.app.ui.history.linkFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** App links (spec §1.0) and the History filters they ask for (dashboard.js › initHistory). */
class DeepLinkTest {

    private fun link(page: String?, fragment: String? = null, vararg params: Pair<String, String>, scheme: String? = LINK_SCHEME) =
        linkRoute(scheme, page, fragment) { key -> params.filter { it.first == key }.map { it.second } }

    @Test fun pages_likeTheWebHash() {
        assertEquals(Dashboard, link("dashboard"))
        assertEquals(AddExpense, link("add"))
        assertEquals(Insights, link("insights"))
        assertEquals(History(), link("history"))
    }

    @Test fun anythingElse_opensNothing() {
        assertNull(link("settings"))
        assertNull(link(null))
        assertNull(link("add", scheme = "https"))
    }

    @Test fun history_incomeByHashOrTab() {
        assertEquals(History(income = true), link("history", "income"))
        assertEquals(History(income = true), link("history", null, "tab" to "income"))
        assertEquals(History(), link("history", null, "tab" to "expenses"))
    }

    @Test fun history_queryDatesAndEveryCategory() {
        val route = link("history", null, "q" to "amazon", "from" to "2026-01-01", "to" to "2026-01-31", "cat" to "Shopping", "cat" to "Food & Dining")
        assertEquals(History(q = "amazon", from = "2026-01-01", to = "2026-01-31", cats = listOf("Shopping", "Food & Dining")), route)
    }

    @Test fun filters_validDatePicksCustom_invalidOneLeftBlank() {
        val f = linkFilters(History(from = "2026-01-01", to = "Jan 31", cats = listOf("Shopping")))
        assertEquals(HistoryRange.CUSTOM, f.range)
        assertEquals("2026-01-01", f.customFrom)
        assertEquals("", f.customTo)
        assertEquals(setOf("Shopping"), f.kinds)
    }

    @Test fun filters_noValidDate_keepsAllTime() {
        val f = linkFilters(History(income = true, from = "bad"))
        assertEquals(HistoryRange.ALL, f.range)
        assertEquals(HistoryTab.INCOME, f.tab)
    }
}
