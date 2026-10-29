package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AiLimitsTest {
    @Test fun `counts only today`() {
        assertEquals(0, aiUsedToday(null, "2026-10-29"))
        assertEquals(3, aiUsedToday("2026-10-29:3", "2026-10-29"))
        assertEquals(0, aiUsedToday("2026-10-28:5", "2026-10-29"))
        assertEquals(0, aiUsedToday("junk", "2026-10-29"))
    }

    @Test fun text() {
        assertEquals("You've used today's 3 CSV imports. You can use it again tomorrow.", aiLimitText(AiFeature.CSV))
    }
}
