package com.spendwise.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// The bell (spec §7.6), ported from helpers.js › notificationsFrom, mergeIds and notifications.js › render.
// Item ids are the web's, so seeing or clearing one on either client syncs through settings.notif.

enum class NotifKind { SPLIT, CLAIM, SETTLED }

data class NotifItem(
    val id: String,
    val kind: NotifKind,
    /** From your side: "i-owe" means you owe them. */
    val type: String,
    val at: Long,
    val day: String,
    val person: String,
    val amount: Double,
    val settles: Boolean = false,
    val note: String,
)

const val NOTIF_MAX = 200

/** Date.parse("YYYY-MM-DD"): a date-only string is UTC midnight; anything else isn't a date (NaN, so 0). */
private fun parseUtcDay(s: String): Long =
    runCatching { LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)

/** Date.parse("YYYY-MM-DDT23:59:59"): local time. */
private fun parseLocalEndOfDay(s: String, zone: ZoneId): Long =
    runCatching { LocalDateTime.parse("${s}T23:59:59").atZone(zone).toInstant().toEpochMilli() }.getOrDefault(0L)

/**
 * notificationsFrom, newest first: each debt shared with you was added (a split bill or a debt) and maybe
 * later marked paid by them; on your own debts, the other person saying it's paid, waiting for you.
 */
fun notificationsFrom(sharedDebts: List<Debt>, ownDebts: List<Debt>, zone: ZoneId = ZoneId.systemDefault()): List<NotifItem> {
    val items = mutableListOf<NotifItem>()
    for (d in ownDebts) {
        if (d.claimDate.isEmpty() || d.settled) continue
        val c = d.claim() ?: continue
        items += NotifItem(
            "${d.id}:claim:${d.claimDate}:${jsNumberText(c.amount)}", NotifKind.CLAIM, d.type,
            parseLocalEndOfDay(d.claimDate, zone), d.claimDate, d.person, c.amount, c.settles, d.notes,
        )
    }
    for (d in sharedDebts) {
        val at = d.createdAt.takeIf { it != 0L } ?: parseUtcDay(d.date)
        items += NotifItem("${d.id}:split", NotifKind.SPLIT, d.type, at, d.date, d.person, d.amount, note = d.notes)
        if (d.settled) {
            val paidAt = parseLocalEndOfDay(d.settledDate, zone).takeIf { it != 0L } ?: at
            items += NotifItem("${d.id}:settled", NotifKind.SETTLED, d.type, maxOf(paidAt, at + 1), d.settledDate.ifEmpty { d.date }, d.person, d.amount, note = d.notes)
        }
    }
    return items.sortedByDescending { it.at } // stable, as JS's sort
}

/** String(n) for an amount, as the id's last part: 1500 not 1500.0, 12.5 as is. */
fun jsNumberText(n: Double): String = if (n % 1.0 == 0.0 && kotlin.math.abs(n) < 1e15) n.toLong().toString() else n.toString()

/** mergeIds: two lists as one, newest last, at most [max]. */
fun mergeIds(a: List<String>, b: List<String>, max: Int = NOTIF_MAX): List<String> = (a + b).distinct().takeLast(max)

/** The unread count on the bell: "9+" past nine. */
fun notifBadge(unread: Int): String = if (unread > 9) "9+" else unread.toString()

/** render: the item's title and its detail line. */
fun notifTitle(i: NotifItem, money: (Double) -> String): String = when (i.kind) {
    NotifKind.SPLIT -> "${i.person} ${if (i.note.startsWith("Split:")) "split a bill" else "added a debt"} with you"
    NotifKind.CLAIM -> "${i.person} says ${money(i.amount)} was paid${if (i.settles) ", all of it" else ""}"
    NotifKind.SETTLED -> "${i.person} marked ${money(i.amount)} as paid"
}

fun notifDetail(i: NotifItem, money: (Double) -> String): String {
    val owed = if (i.type == I_OWE) "You owe ${money(i.amount)}" else "${i.person} owes you ${money(i.amount)}"
    val lead = when (i.kind) { NotifKind.SPLIT -> owed; NotifKind.CLAIM -> "Confirm it on Debts"; NotifKind.SETTLED -> "" }
    return listOf(lead, i.note).filter { it.isNotEmpty() }.joinToString(" · ")
}

const val NOTIF_EMPTY = "Nothing yet. When someone splits a bill or adds a debt with your email, it shows up here."
const val NOTIF_CAUGHT_UP = "You're all caught up."
