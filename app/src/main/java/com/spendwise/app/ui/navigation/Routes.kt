package com.spendwise.app.ui.navigation

import kotlinx.serialization.Serializable

// Destinations from spec §1.0: four tabs plus More (Income, Investments, Debts, Settings).

@Serializable data object Dashboard
/**
 * [income]: open on the Income tab (the Income page's "All income"). The rest come from a link, as the
 * web's history.html?q=&from=&to=&cat= (dashboard.js › initHistory).
 */
@Serializable data class History(
    val income: Boolean = false,
    val q: String? = null,
    val from: String? = null,
    val to: String? = null,
    val cats: List<String> = emptyList(),
)
@Serializable data object AddExpense
@Serializable data object Voice
@Serializable data object Insights
@Serializable data object Income
@Serializable data object Investments
@Serializable data object Debts
@Serializable data object Settings
@Serializable data object CsvImport
@Serializable data class EditExpense(val id: String)
@Serializable data class EditIncome(val id: String)
