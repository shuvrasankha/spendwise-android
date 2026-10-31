package com.spendwise.app.domain

// What the Worker needs for bill-due alerts and the month-end summary (dashboard.js › syncPush), and how a
// pushed message is shown (sw.js). The Worker side is in the web repo's worker/src/push.js.

const val PUSH_MAX_BILLS = 50

/** syncPush › syncBills: each active series' next due date, at most 50. */
fun pushBills(expenses: List<Expense>, today: String, skipped: SkippedBill): List<UpcomingBill> =
    upcomingBills(expenses, today, skipped).take(PUSH_MAX_BILLS)

/** syncPush › syncMonthTotals: spending (not savings) this month and last, each rounded as Math.round does. */
fun pushMonthTotals(expenses: List<Expense>, today: String): Map<String, Long> {
    val month = today.take(7)
    fun spentIn(m: String) = Math.round(expenses.filter { isSpending(it.category) && it.date.startsWith(m) }.sumOf { it.amount })
    return linkedMapOf(month to spentIn(month), addMonths(month, -1) to spentIn(addMonths(month, -1)))
}

/** The app page a pushed message's web url opens, as a spendwise:// link host. */
fun pushPage(url: String?): String = when (url) {
    "debt.html" -> "debts"
    "insights.html" -> "insights"
    else -> "dashboard"
}

/** Which notification channel a message goes to, by its tag: shared debts, or bills and summaries. */
fun pushIsDebt(tag: String?): Boolean = tag != null && (tag.startsWith("debt-") || tag.startsWith("claim-"))

private val SHARED_TITLE = Regex("""(.+) (split a bill|added a debt) with you""")
private val YOU_OWE = Regex("""You owe (\S+)(?: · (.+))?""")
private val OWES_YOU = Regex("""(.+) owes you (\S+)(?: · (.+))?""")

/**
 * The Worker's debt alerts ("X added a debt with you" / "You owe ₹600"), reworded so the amount leads and the
 * sender is named in the body. Anything that doesn't match is shown as sent (the web shows the same text).
 */
fun prettyPush(title: String, body: String): Pair<String, String> {
    val shared = SHARED_TITLE.matchEntire(title) ?: return title to body
    val person = shared.groupValues[1]
    val did = if (shared.groupValues[2] == "split a bill") "split a bill with you" else "added a debt with you"
    fun text(note: String, tap: String) = "$person $did." + (if (note.isEmpty()) "" else " Note: $note.") + " $tap"
    YOU_OWE.matchEntire(body)?.let { m ->
        val lead = if (did.startsWith("split")) "Your share is" else "You owe"
        return "$lead ${m.groupValues[1]}" to text(m.groupValues[2], "Tap to review it in Debts.")
    }
    OWES_YOU.matchEntire(body)?.let { m -> return "${m.groupValues[2]} owed to you" to text(m.groupValues[3], "Tap to see it in Debts.") }
    return title to body
}

const val REMINDER_TITLE = "Log today's spending"
const val REMINDER_TEXT = "Add what you spent today. It only takes a few seconds."

/** helpers.js › pushTagsFor: the pushes in the tray that belong to bell items (debt-<id> for a split, claim-<id> for a claim). */
fun pushTagsFor(itemIds: List<String>): Set<String> = itemIds.mapNotNullTo(linkedSetOf()) { id ->
    val parts = id.split(':')
    when (parts.getOrNull(1)) {
        "split" -> "debt-${parts[0]}"
        "claim" -> "claim-${parts[0]}"
        else -> null
    }
}
