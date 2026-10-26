package com.spendwise.app.domain

// Ported from helpers.js › EXPENSE_CATEGORIES, CATEGORY_ALIASES, normalizeCategory, isSpending (spec §3.1, §6.1)

/** In the order the Add form shows them. */
val EXPENSE_CATEGORIES = listOf(
    "Food & Dining", "Groceries", "Transport", "Travel", "Shopping", "Bills & Utilities",
    "Home & Rent", "Health", "Entertainment", "Education", "Savings & Investments", "Other",
)

/** Older names, merged or renamed. Saved entries keep the old name; they're shown and counted under the new one. */
val CATEGORY_ALIASES = mapOf(
    "Subscription" to "Bills & Utilities",
    "Personal Care" to "Shopping",
    "Gifts" to "Other",
    "Rent" to "Home & Rent",
    "Investment" to "Savings & Investments",
)

const val SAVINGS_CATEGORY = "Savings & Investments"

fun normalizeCategory(category: String?): String {
    val name = category.orEmpty().trim()
    return CATEGORY_ALIASES[name] ?: name.ifEmpty { "Other" }
}

/** Money put into savings or investments is saved, not spent: spending totals leave it out. */
fun isSpending(category: String?): Boolean = normalizeCategory(category) != SAVINGS_CATEGORY
