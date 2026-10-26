package com.spendwise.app.domain

private val leadingNumber = Regex("""^[+-]?(Infinity|(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?)""")
private val wholeNumber = Regex("""^[+-]?(Infinity|(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?)$""")

/** JS `parseFloat(s)`: the longest leading number ("12abc" → 12), NaN when there is none. */
fun jsParseFloat(s: String): Double =
    leadingNumber.find(s.trimStart { it.isWhitespace() || it == '﻿' })?.value?.toDouble() ?: Double.NaN

/** JS `Number(raw) || 0`, used where the web reads with Number() rather than parseFloat: "12abc" → 0, true → 1. */
fun jsNumberOrZero(raw: Any?): Double {
    val n = when (raw) {
        is Number -> raw.toDouble()
        is Boolean -> if (raw) 1.0 else 0.0
        is String -> raw.trim { it.isWhitespace() || it == '﻿' }.let { s ->
            if (s.isEmpty()) 0.0 else if (wholeNumber.matches(s)) s.toDouble() else Double.NaN
        }
        else -> Double.NaN
    }
    return if (n.isNaN()) 0.0 else n
}
