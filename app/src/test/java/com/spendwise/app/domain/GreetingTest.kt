package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test fun `by the hour`() {
        assertEquals("Good morning, Shuvra", greeting(5, "Shuvra Paul"))
        assertEquals("Good afternoon, Shuvra", greeting(12, "Shuvra"))
        assertEquals("Good evening, Shuvra", greeting(17, "Shuvra"))
        assertEquals("Hello, Shuvra", greeting(22, "Shuvra"))
        assertEquals("Hello, Shuvra", greeting(3, "Shuvra"))
    }

    @Test fun `no name`() {
        assertEquals("Good morning", greeting(9, ""))
        assertEquals("Good morning", greeting(9, "  "))
    }
}
