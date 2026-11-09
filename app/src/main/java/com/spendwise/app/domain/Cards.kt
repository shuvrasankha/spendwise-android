package com.spendwise.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

// Saved cards. There's no card list in Firestore (settings takes only its own keys, and a new collection needs the
// web's rules): the account's cards are the names on its card expenses, so they sync and the web shows them. A card
// added on the Cards page and not used yet, and a card removed from the list, are kept on this device only.

/** A card: its name as first saved, and [type] "Credit Card" or "Debit Card". */
@Serializable
data class Card(val name: String, val type: String) {
    val key get() = cardKey(name, type)
    val short get() = if (type == "Credit Card") "Credit" else "Debit"
}

/** Names that differ only in case or spacing are the same card. */
fun cardKey(name: String, type: String) = type + ":" + name.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * The cards to offer: those on card expenses, most recently used first, then [added] ones not used yet. A name
 * keeps its earliest spelling; [hidden] keys are left out.
 */
fun savedCards(expenses: List<Expense>, added: List<Card> = emptyList(), hidden: Set<String> = emptySet()): List<Card> {
    val used = expenses.filter { isCard(it.payment) && it.cardName.isNotBlank() }
    val spelling = mutableMapOf<String, String>()
    used.sortedBy { it.date }.forEach { spelling.putIfAbsent(cardKey(it.cardName, it.payment), it.cardName.trim()) }
    val recent = used.sortedByDescending { it.date }.map { Card(spelling.getValue(cardKey(it.cardName, it.payment)), it.payment) }
    return (recent + added).distinctBy { it.key }.filter { it.key !in hidden }
}

/** The saved spelling of a typed card name, so "hdfc  millennia" stays one card with "HDFC Millennia". */
fun matchCard(name: String, type: String, cards: List<Card>): String {
    val typed = name.trim().replace(Regex("\\s+"), " ")
    return cards.firstOrNull { it.key == cardKey(typed, type) }?.name ?: typed
}

/** The card a bank SMS points to: the one saved card of that type whose name starts with the bank, else none. */
fun cardForBank(bank: String, type: String, cards: List<Card>): String? {
    val b = bank.trim().lowercase().takeIf { it.isNotEmpty() } ?: return null
    return cards.filter { it.type == type && it.name.trim().lowercase().startsWith(b) }.singleOrNull()?.name
}

data class CardSpend(val card: Card, val thisMonth: Double, val lastMonth: Double, val count: Int)

/** Spending per card, counted as the month total is (spending categories, the 1st to today). */
fun cardSpending(expenses: List<Expense>, cards: List<Card>, today: LocalDate): List<CardSpend> {
    val ms = today.withDayOfMonth(1).toString()
    val last = today.withDayOfMonth(1).minusMonths(1)
    val lms = last.toString()
    val lme = last.withDayOfMonth(last.lengthOfMonth()).toString()
    val todayS = today.toString()
    val byCard = expenses.filter { isCard(it.payment) && it.cardName.isNotBlank() && isSpending(it.category) }.groupBy { cardKey(it.cardName, it.payment) }
    return cards.map { c ->
        val list = byCard[c.key].orEmpty()
        val now = list.filter { it.date in ms..todayS }
        CardSpend(c, now.sumOf { it.amount }, list.filter { it.date in lms..lme }.sumOf { it.amount }, now.size)
    }
}

/** The More row's line. */
fun cardsSummary(spends: List<CardSpend>, money: (Double) -> String): String = when {
    spends.isEmpty() -> "No cards yet"
    else -> {
        val n = spends.count { it.count > 0 }
        val total = spends.sumOf { it.thisMonth }
        if (total > 0) "${money(total)} this month on ${if (n == 1) "1 card" else "$n cards"}" else "Nothing on cards this month"
    }
}

/** Card colours, apart from the ones that mean something: green (money in), red (owed), yellow (accent), orange. */
val CARD_COLOURS = listOf("Blue", "Violet", "Teal", "Pink", "Brown", "Slate", "Orange")

/**
 * Each card's colour: the one chosen for it, else a default from its name, moved to the next free colour when
 * another card has it. Defaults are given in name order, so a card keeps its colour as cards are used.
 * shortcut: a new card can move an unchosen card's default when they clash; picking a colour pins it.
 */
fun cardColours(cards: List<Card>, chosen: Map<String, String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    cards.forEach { c -> chosen[c.key]?.takeIf { it in CARD_COLOURS }?.let { out[c.key] = it } }
    cards.filter { it.key !in out }.sortedBy { it.key }.forEach { c ->
        val start = Math.floorMod(c.key.hashCode(), CARD_COLOURS.size)
        val free = CARD_COLOURS.indices.map { CARD_COLOURS[(start + it) % CARD_COLOURS.size] }.firstOrNull { it !in out.values }
        out[c.key] = free ?: CARD_COLOURS[start]
    }
    return out
}

private val json = Json { ignoreUnknownKeys = true }

fun readCards(text: String?): List<Card> =
    if (text.isNullOrEmpty()) emptyList() else runCatching { json.decodeFromString<List<Card>>(text) }.getOrDefault(emptyList())

fun writeCards(cards: List<Card>): String = json.encodeToString(cards)

fun readCardColours(text: String?): Map<String, String> =
    if (text.isNullOrEmpty()) emptyMap() else runCatching { json.decodeFromString<Map<String, String>>(text) }.getOrDefault(emptyMap())

fun writeCardColours(colours: Map<String, String>): String = json.encodeToString(colours)
