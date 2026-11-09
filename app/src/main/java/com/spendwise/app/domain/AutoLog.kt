package com.spendwise.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

// Bank SMS read from the SMS app's notifications wait here until approved in the app. Kept on the device only;
// the SMS text itself is never stored, only what parseSms read from it.

/** A payment waiting for approval. [id] is the message's fingerprint, so the same SMS is never queued twice. */
@Serializable
data class ReviewItem(
    val id: String,
    val amount: Double,
    val date: String,
    val category: String,
    val payment: String,
    val cardName: String,
    val description: String,
)

/** At most this many wait; the oldest goes first, so weeks away can't grow the list without end. */
const val REVIEW_LIMIT = 50

/** Fingerprints remembered, so a conversation notification posted again isn't read again. */
const val SEEN_LIMIT = 200

fun reviewItem(id: String, e: SmsEntry.Spent) = ReviewItem(id, e.amount, e.date, e.category, e.payment, e.cardName, e.description)

/** One SMS: the same text at the same time. Messages repost a whole conversation on every new message. */
fun smsFingerprint(text: String, postedAt: Long): String = "${text.trim().hashCode().toUInt().toString(36)}-${postedAt.toString(36)}"

/** Adds [item] at the end unless it's already there, then drops the oldest past [REVIEW_LIMIT]. */
fun queued(list: List<ReviewItem>, item: ReviewItem): List<ReviewItem> =
    if (list.any { it.id == item.id }) list else (list + item).takeLast(REVIEW_LIMIT)

/** The expense to save, with the description as the person left it (the read one when they cleared it). */
fun ReviewItem.toInput(description: String): ExpenseInput =
    ExpenseInput(amount, category, date, payment, cardName, sanitize(description, 300).ifEmpty { this.description }, "", emptyList())

/** An expense of the same amount on the same day: they may have added it by hand already. */
fun possiblyAdded(item: ReviewItem, expenses: List<Expense>): Boolean =
    expenses.any { it.date == item.date && abs(it.amount - item.amount) < 0.005 }

private val json = Json { ignoreUnknownKeys = true }

fun readReview(text: String?): List<ReviewItem> =
    if (text.isNullOrEmpty()) emptyList() else runCatching { json.decodeFromString<List<ReviewItem>>(text) }.getOrDefault(emptyList())

fun writeReview(list: List<ReviewItem>): String = json.encodeToString(list)
