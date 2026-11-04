package com.spendwise.app.domain

/** "v1.2-abc123" → [1, 2]; a tag with no leading number reads as no update. */
private fun versionParts(v: String) = Regex("""^v?(\d+(?:\.\d+)*)""").find(v)?.groupValues?.get(1)?.split('.')?.map { it.toInt() }

/** A release [tag] is newer than the [installed] versionName, compared part by part ("1.6.10" > "1.6.9"). */
fun isNewerVersion(tag: String, installed: String): Boolean {
    val a = versionParts(tag) ?: return false
    val b = versionParts(installed) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}
