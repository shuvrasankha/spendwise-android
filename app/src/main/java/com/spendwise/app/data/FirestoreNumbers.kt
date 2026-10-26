package com.spendwise.app.data

import com.spendwise.app.domain.jsParseFloat

// How the web reads and writes amounts (spec §3.0, §3.7, §9.10)

/**
 * Reads an amount the way the web does: `parseFloat(raw)`, with NaN as 0.
 * Accepts Long, Double, or a string with a leading number ("250", " 12.5", "12abc" → 12).
 */
fun readAmount(raw: Any?): Double {
    val value = when (raw) {
        is Number -> raw.toDouble()
        is String -> jsParseFloat(raw)
        else -> Double.NaN
    }
    return if (value.isNaN()) 0.0 else value
}

private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0

/**
 * What the JS SDK would store for [value]: a Long for a safe whole number, a Double otherwise.
 * Mirrors the SDK's isSafeInteger check, which also sends -0 as a double.
 */
fun toFirestoreNumber(value: Double): Number {
    val whole = value == Math.floor(value) && value >= -MAX_SAFE_INTEGER && value <= MAX_SAFE_INTEGER
    val negativeZero = value == 0.0 && 1 / value < 0
    return if (whole && !negativeZero) value.toLong() else value
}
