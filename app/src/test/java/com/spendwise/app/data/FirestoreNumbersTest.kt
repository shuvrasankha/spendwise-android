package com.spendwise.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreNumbersTest {

    @Test fun readAmount_numbers() {
        assertEquals(250.0, readAmount(250L), 0.0)
        assertEquals(182.5, readAmount(182.5), 0.0)
        assertEquals(7.0, readAmount(7), 0.0)
    }

    @Test fun readAmount_numericStringsLikeParseFloat() {
        assertEquals(250.0, readAmount("250"), 0.0)
        assertEquals(1499.5, readAmount("1499.5"), 0.0)
        assertEquals(12.5, readAmount("  12.5"), 0.0)
        assertEquals(0.5, readAmount(".5"), 0.0)
        assertEquals(1.0, readAmount("1."), 0.0)
        assertEquals(-3.0, readAmount("-3"), 0.0)
        assertEquals(1500.0, readAmount("1.5e3"), 0.0)
        assertEquals(1.0, readAmount("1e"), 0.0) // an exponent needs digits
        assertEquals(12.0, readAmount("12abc"), 0.0) // web quirk: spec §9.10
        assertEquals(1.0, readAmount("1,000"), 0.0) // parseFloat stops at the comma
        assertEquals(Double.POSITIVE_INFINITY, readAmount("Infinity"), 0.0)
    }

    @Test fun readAmount_anythingElseIsZero() {
        assertEquals(0.0, readAmount(null), 0.0)
        assertEquals(0.0, readAmount(""), 0.0)
        assertEquals(0.0, readAmount("abc"), 0.0)
        assertEquals(0.0, readAmount("YUVV"), 0.0) // legacy XOR "250" (spec §5) shows as 0, as on the web
        assertEquals(0.0, readAmount(true), 0.0)
        assertEquals(0.0, readAmount(Double.NaN), 0.0)
        assertEquals(0.0, readAmount(mapOf("a" to 1)), 0.0)
    }

    @Test fun toFirestoreNumber_wholeIsLongFractionIsDouble() {
        assertEquals(4250L, toFirestoreNumber(4250.0))
        assertEquals(0L, toFirestoreNumber(0.0))
        assertEquals(-12L, toFirestoreNumber(-12.0))
        assertEquals(182.5, toFirestoreNumber(182.5))
        assertEquals(9_007_199_254_740_991L, toFirestoreNumber(9_007_199_254_740_991.0))
    }

    @Test fun toFirestoreNumber_edgesStayDouble() {
        assertTrue(toFirestoreNumber(-0.0) is Double)
        assertTrue(toFirestoreNumber(9_007_199_254_740_992.0) is Double)
        assertTrue(toFirestoreNumber(Double.NaN) is Double)
        assertTrue(toFirestoreNumber(Double.POSITIVE_INFINITY) is Double)
    }

    @Test fun roundTrip_writeThenReadGivesTheSameValue() {
        listOf(0.0, 1.0, 99.99, 4250.0, 1e6, 0.1 + 0.2).forEach {
            assertEquals(it, readAmount(toFirestoreNumber(it)), 0.0)
        }
    }
}
