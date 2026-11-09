package com.spendwise.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

// Bank SMS read from the SMS app's notifications wait here until approved in the app. Kept on the device only;
// the SMS text itself is never stored, only what parseSms read from it.

/**
 * A payment or money in waiting for approval. [id] is the message's fingerprint, so the same SMS is never queued
 * twice. [payee] is who the SMS named, kept as read, so what's learned about it applies to the next one.
 * Money in ([income]): [description] is the source, [payment] the payment type, [bank] the bank; no category.
 */
@Serializable
data class ReviewItem(
    val id: String,
    val amount: Double,
    val date: String,
    val category: String,
    val payment: String,
    val cardName: String,
    val description: String,
    val payee: String = description,
    val income: Boolean = false,
    val bank: String = "",
)

/** What was last chosen for a payee: the category (spends only) and the description or source. */
@Serializable
data class Learned(val category: String = "", val description: String = "")

/** Payees remembered; the least recently used goes first. */
const val LEARNED_LIMIT = 300

/** At most this many wait; the oldest goes first, so weeks away can't grow the list without end. */
const val REVIEW_LIMIT = 50

/** Fingerprints remembered, so a conversation notification posted again isn't read again. */
const val SEEN_LIMIT = 200

fun reviewItem(id: String, e: SmsEntry.Spent) = ReviewItem(id, e.amount, e.date, e.category, e.payment, e.cardName, e.description)

fun reviewItem(id: String, e: SmsEntry.Earned) = ReviewItem(id, e.amount, e.date, "", e.paymentType, "", e.source, income = true, bank = e.bank)

// parseSms's fallbacks name nobody, so nothing is learned about them
private val GENERIC = setOf("bank payment", "bank credit", "salary", "refund", "interest", "cashback")

/** The memory key for a payee, money in and out apart; null when the SMS named nobody in particular. */
fun payeeKey(item: ReviewItem): String? =
    item.payee.trim().lowercase().takeIf { it.isNotEmpty() && it !in GENERIC }?.let { (if (item.income) "in:" else "out:") + it }

/** [item] as this payee was last saved: its category (when still one of the list) and description. */
fun ReviewItem.withLearned(memory: Map<String, Learned>): ReviewItem {
    val l = payeeKey(this)?.let(memory::get) ?: return this
    return copy(
        category = l.category.takeIf { !income && it in EXPENSE_CATEGORIES } ?: category,
        description = l.description.ifBlank { description },
    )
}

/** Remembers what was saved for [item]'s payee, newest last, dropping the oldest past [LEARNED_LIMIT]. */
fun learned(memory: Map<String, Learned>, item: ReviewItem, category: String, description: String): Map<String, Learned> {
    val key = payeeKey(item) ?: return memory
    val entry = Learned(if (item.income) "" else category, description.trim())
    return (memory - key + (key to entry)).entries.toList().takeLast(LEARNED_LIMIT).associate { it.key to it.value }
}

/** The list once [saved] is saved: it leaves, and the others from that payee take what was just learned. */
fun afterSaving(list: List<ReviewItem>, saved: ReviewItem, memory: Map<String, Learned>): List<ReviewItem> {
    val key = payeeKey(saved)
    return list.filter { it.id != saved.id }.map { if (key != null && payeeKey(it) == key) it.withLearned(memory) else it }
}

/** One SMS: the same text at the same time. Messages repost a whole conversation on every new message. */
fun smsFingerprint(text: String, postedAt: Long): String = "${text.trim().hashCode().toUInt().toString(36)}-${postedAt.toString(36)}"

/** Adds [item] at the end unless it's already there, then drops the oldest past [REVIEW_LIMIT]. */
fun queued(list: List<ReviewItem>, item: ReviewItem): List<ReviewItem> =
    if (list.any { it.id == item.id }) list else (list + item).takeLast(REVIEW_LIMIT)

/** The expense to save, with the description as the person left it (the read one when they cleared it). */
fun ReviewItem.toInput(description: String): ExpenseInput =
    ExpenseInput(amount, category, date, payment, cardName, sanitize(description, 300).ifEmpty { this.description }, "", emptyList())

/** Money in to save, or why it can't be saved as it is (online with no bank: it needs Edit). */
fun ReviewItem.toIncome(source: String): IncomeCheck =
    checkIncome(amount.toBigDecimal().stripTrailingZeros().toPlainString(), date, source.trim().ifEmpty { description }, payment, bank, "", adding = true)

/** An expense of the same amount on the same day: they may have added it by hand already. */
fun possiblyAdded(item: ReviewItem, expenses: List<Expense>): Boolean =
    expenses.any { it.date == item.date && abs(it.amount - item.amount) < 0.005 }

/** The same for money in, against the income entries. */
@JvmName("possiblyAddedIncome")
fun possiblyAdded(item: ReviewItem, income: List<Income>): Boolean =
    income.any { it.date == item.date && abs(it.amount - item.amount) < 0.005 }

private val json = Json { ignoreUnknownKeys = true }

fun readReview(text: String?): List<ReviewItem> =
    if (text.isNullOrEmpty()) emptyList() else runCatching { json.decodeFromString<List<ReviewItem>>(text) }.getOrDefault(emptyList())

fun writeReview(list: List<ReviewItem>): String = json.encodeToString(list)

fun readLearned(text: String?): Map<String, Learned> =
    if (text.isNullOrEmpty()) emptyMap() else runCatching { json.decodeFromString<Map<String, Learned>>(text) }.getOrDefault(emptyMap())

fun writeLearned(memory: Map<String, Learned>): String = json.encodeToString(memory)
