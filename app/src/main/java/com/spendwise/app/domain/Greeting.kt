package com.spendwise.app.domain

/** "Good morning, Shuvra": by the hour (0-23), with the first word of [name] when there is one. */
fun greeting(hour: Int, name: String): String {
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
    val first = name.trim().substringBefore(' ')
    return if (first.isEmpty()) part else "$part, $first"
}
