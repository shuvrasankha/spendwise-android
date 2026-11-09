package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CardsTest {
    private fun e(card: String, date: String, amount: Double = 100.0, payment: String = "Credit Card", category: String = "Shopping") =
        Expense(date + card, amount, category, date, payment, card, "", "", emptyList())

    private val today = LocalDate.of(2026, 11, 9)

    @Test fun cards_comeFromExpenses_newestFirst_oneSpellingEach() {
        val list = savedCards(listOf(e("HDFC Millennia", "2026-10-01"), e("hdfc  millennia ", "2026-11-05"), e("SBI", "2026-11-01", payment = "Debit Card"), e("", "2026-11-07"), e("X", "2026-11-08", payment = "UPI")))
        assertEquals(listOf(Card("HDFC Millennia", "Credit Card"), Card("SBI", "Debit Card")), list)
    }

    @Test fun added_andHidden() {
        val added = listOf(Card("Amex", "Credit Card"), Card("hdfc millennia", "Credit Card"))
        val list = savedCards(listOf(e("HDFC Millennia", "2026-11-01")), added, hidden = setOf(cardKey("SBI", "Debit Card")))
        assertEquals(listOf("HDFC Millennia", "Amex"), list.map { it.name })
        assertEquals(listOf("Amex"), savedCards(listOf(e("HDFC Millennia", "2026-11-01")), added, setOf(cardKey("HDFC Millennia", "Credit Card"))).map { it.name })
        // The same name as credit and debit is two cards
        assertEquals(2, savedCards(listOf(e("HDFC", "2026-11-01"), e("HDFC", "2026-11-02", payment = "Debit Card"))).size)
    }

    @Test fun typedName_takesTheSavedSpelling() {
        val cards = listOf(Card("HDFC Millennia", "Credit Card"))
        assertEquals("HDFC Millennia", matchCard(" hdfc   MILLENNIA ", "Credit Card", cards))
        assertEquals("hdfc millennia", matchCard("hdfc millennia", "Debit Card", cards))
        assertEquals("New card", matchCard(" New  card", "Credit Card", cards))
    }

    @Test fun bankSms_picksTheOnlyMatchingCard() {
        val cards = listOf(Card("HDFC Millennia", "Credit Card"), Card("HDFC Regalia", "Credit Card"), Card("SBI Debit", "Debit Card"))
        assertEquals("SBI Debit", cardForBank("SBI", "Debit Card", cards))
        assertNull(cardForBank("HDFC", "Credit Card", cards)) // two: they choose
        assertNull(cardForBank("SBI", "Credit Card", cards))
        assertNull(cardForBank("", "Debit Card", cards))
    }

    @Test fun spending_countsLikeTheMonthTotal() {
        val c = Card("HDFC", "Credit Card")
        val list = listOf(
            e("HDFC", "2026-11-01", 200.0), e("hdfc", "2026-11-09", 50.0), e("HDFC", "2026-11-10", 999.0), // future: not yet
            e("HDFC", "2026-10-31", 70.0), e("HDFC", "2026-11-02", 500.0, category = "Savings & Investments"),
        )
        val s = cardSpending(list, listOf(c), today).single()
        assertEquals(250.0, s.thisMonth, 0.0)
        assertEquals(70.0, s.lastMonth, 0.0)
        assertEquals(2, s.count)
        assertEquals("₹250 this month on 1 card", cardsSummary(listOf(s)) { "₹${it.toInt()}" })
        assertEquals("No cards yet", cardsSummary(emptyList()) { "" })
    }

    @Test fun storedJson_roundTrips() {
        val l = listOf(Card("Amex", "Credit Card"))
        assertEquals(l, readCards(writeCards(l)))
        assertEquals(emptyList<Card>(), readCards("bad"))
    }

    @Test fun colours_chosenFirst_thenDistinctDefaults_thatDontMoveWithUse() {
        val cards = CARD_COLOURS.indices.map { Card("C$it", "Credit Card") }
        val auto = cardColours(cards, emptyMap())
        assertEquals(CARD_COLOURS.toSet(), auto.values.toSet()) // six cards, six colours
        assertEquals(auto, cardColours(cards.reversed(), emptyMap())) // order of use doesn't matter
        val picked = cardColours(cards, mapOf(cards[0].key to "Pink", "gone" to "Blue", cards[1].key to "Red"))
        assertEquals("Pink", picked[cards[0].key])
        // "Red" isn't offered, so that card takes a default; the others move round the pick and stay distinct
        assertEquals(CARD_COLOURS.toSet(), picked.values.toSet())
        assertEquals(mapOf("a" to "Teal"), readCardColours(writeCardColours(mapOf("a" to "Teal"))))
    }
}
