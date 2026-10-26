package com.spendwise.app.domain

private val leadingNumber = Regex("""^[+-]?(Infinity|(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?)""")

/** JS `parseFloat(s)`: the longest leading number ("12abc" → 12), NaN when there is none. */
fun jsParseFloat(s: String): Double =
    leadingNumber.find(s.trimStart { it.isWhitespace() || it == '﻿' })?.value?.toDouble() ?: Double.NaN
