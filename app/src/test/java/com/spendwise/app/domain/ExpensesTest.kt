package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpensesTest {

    private fun check(
        amount: String = "250",
        category: String = "Food & Dining",
        date: String = "2026-10-26",
        payment: String = "UPI",
        cardName: String = "",
        description: String = "Lunch",
        notes: String = "",
        tags: List<String> = emptyList(),
    ) = checkExpense(amount, category, date, payment, cardName, description, notes, tags)

    private fun ok(result: ExpenseCheck) = (result as ExpenseCheck.Ok).input

    @Test fun validation_inTheWebsOrder() {
        listOf("", "0", "-5", "abc", ".").forEach {
            assertEquals(it, ExpenseCheck.Invalid("Enter a valid amount."), check(amount = it))
        }
        assertEquals(ExpenseCheck.Invalid("Enter a valid amount."), check(amount = "", category = ""))
        assertEquals(ExpenseCheck.Invalid("Select a category."), check(category = ""))
        assertEquals(ExpenseCheck.Invalid("Select a date."), check(date = ""))
    }

    @Test fun amount_isParseFloatAndNotRounded() {
        assertEquals(250.0, ok(check(amount = "250")).amount, 0.0)
        assertEquals(12.345, ok(check(amount = "12.345")).amount, 0.0)
        assertEquals(0.5, ok(check(amount = ".5")).amount, 0.0)
    }

    @Test fun cleaning_descriptionNotesAndCardName() {
        val input = ok(check(description = "  ", notes = "  a\u0000b\tc  ", payment = "Credit Card", cardName = " HDFC "))
        assertEquals("-", input.description)
        assertEquals("ab\tc", input.notes)
        assertEquals("HDFC", input.cardName)
        assertEquals(300, ok(check(description = "x".repeat(400))).description.length)
        assertEquals(500, ok(check(notes = "y".repeat(600))).notes.length)
        assertEquals(100, ok(check(payment = "Debit Card", cardName = "z".repeat(150))).cardName.length)
    }

    @Test fun cardName_isEmptyUnlessTheMethodIsACard() {
        assertEquals("", ok(check(payment = "UPI", cardName = "HDFC")).cardName)
        assertEquals("", ok(check(payment = "Cash", cardName = "HDFC")).cardName)
        assertEquals("HDFC", ok(check(payment = "Credit Card", cardName = "HDFC")).cardName)
    }

    @Test fun sanitize_keepsNewlinesAndTabs() {
        assertEquals("a\nb\tc", sanitize("a\nb\tc\u0007\u007F"))
        assertEquals("abc", sanitize("  abc  "))
        assertEquals("ab", sanitize("abcdef", 2))
    }

    @Test fun addTags_splitsOnCommasAndSpacesDropsHashAndDuplicates() {
        assertEquals(listOf("goa", "trip"), addTags(emptyList(), "#goa, trip"))
        assertEquals(listOf("goa", "trip", "food"), addTags(listOf("goa"), "trip goa  #food,"))
        assertEquals(listOf("Goa", "goa"), addTags(listOf("Goa"), "goa")) // case is kept, so these differ
        assertEquals(listOf("a"), addTags(listOf("a"), " , "))
    }

    @Test fun lists_matchTheWeb() {
        assertEquals(listOf("UPI", "Cash", "Credit Card", "Debit Card", "Net Banking", "Other"), PAYMENT_METHODS)
        assertEquals(EXPENSE_CATEGORIES, CATEGORY_TILE_LABELS.keys.toList())
        assertTrue(isCard("Credit Card") && isCard("Debit Card") && !isCard("UPI"))
    }

    @Test fun duplicateKey_isAmountCategoryDate() {
        val a = ok(check())
        assertEquals(duplicateKey(a), duplicateKey(a.copy(description = "other", payment = "Cash")))
        assertTrue(duplicateKey(a) != duplicateKey(a.copy(date = "2026-10-25")))
    }

    @Test fun orderedTiles_mostUsedInLast60Days_tiesKeepOrder() {
        val today = java.time.LocalDate.parse("2026-10-26")
        val tiles = orderedTiles(
            listOf(
                ex(1.0, "Travel", "2026-10-01", id = "a"), ex(1.0, "Travel", "2026-08-27", id = "b"), // 60 days back counts
                ex(1.0, "Health", "2026-10-20", id = "c"),
                ex(1.0, "Shopping", "2026-08-26", id = "d"), ex(1.0, "Shopping", "2026-08-01", id = "e"), // too old
            ),
            today,
        ).map { it.second }
        assertEquals(listOf("Travel", "Health", "Food", "Groceries", "Transport", "Shopping"), tiles.take(6))
        assertEquals(CATEGORY_TILE_LABELS.values.toList(), orderedTiles(emptyList(), today).map { it.second })
    }
}
