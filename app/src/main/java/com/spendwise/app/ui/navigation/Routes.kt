package com.spendwise.app.ui.navigation

import kotlinx.serialization.Serializable

// Destinations from spec §1.0: four tabs plus More (Income, Investments, Debts, Settings).

@Serializable data object Dashboard
/** [income]: open on the Income tab (the Income page's "All income"). */
@Serializable data class History(val income: Boolean = false)
@Serializable data object AddExpense
@Serializable data object Insights
@Serializable data object Income
@Serializable data object Investments
@Serializable data object Debts
@Serializable data object Settings
@Serializable data class EditExpense(val id: String)
@Serializable data class EditIncome(val id: String)
