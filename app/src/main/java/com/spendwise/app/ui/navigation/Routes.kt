package com.spendwise.app.ui.navigation

import kotlinx.serialization.Serializable

// Destinations from spec §1.0: four tabs plus More (Income, Investments, Debts, Settings).

@Serializable data object Dashboard
@Serializable data object History
@Serializable data object AddExpense
@Serializable data object Insights
@Serializable data object Income
@Serializable data object Investments
@Serializable data object Debts
@Serializable data object Settings
