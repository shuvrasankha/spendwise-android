package com.spendwise.app.domain

/**
 * Android-only daily caps per AI feature, per account, counted on this device (the Worker's 100 a day for
 * web and Android together still applies). Reinstalling resets them on that phone.
 */
enum class AiFeature(val key: String, val perDay: Int, val what: String) {
    COACH("coach", 5, "AI coach answers"),
    ASK("ask", 10, "AI questions"),
    VOICE("voice", 20, "voice and receipt readings"),
    CSV("csv", 3, "CSV imports"),
}

/** Today's count from the stored "YYYY-MM-DD:n"; any other day's, or a bad value, is 0. */
fun aiUsedToday(stored: String?, today: String): Int =
    stored?.split(':')?.takeIf { it.size == 2 && it[0] == today }?.get(1)?.toIntOrNull() ?: 0

fun aiLimitText(f: AiFeature): String = "You've used today's ${f.perDay} ${f.what}. You can use it again tomorrow."
