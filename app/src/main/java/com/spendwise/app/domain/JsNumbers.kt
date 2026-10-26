package com.spendwise.app.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

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

/** JS Number(v) for a JSON value: "8020" is 8020, true is 1, null is 0; missing or not a number is NaN. */
fun jsNumberOf(v: JsonElement?): Double = when (v) {
    null -> Double.NaN
    JsonNull -> 0.0
    is JsonPrimitive -> when {
        v.isString -> v.content.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }
        v.booleanOrNull != null -> if (v.booleanOrNull == true) 1.0 else 0.0
        else -> v.content.toDoubleOrNull() ?: Double.NaN
    }
    is JsonArray, is JsonObject -> Double.NaN // shortcut: JS reads [] as 0 and [5] as 5; both fail the > 0 checks anyway
}
