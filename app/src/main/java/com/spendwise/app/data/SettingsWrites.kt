package com.spendwise.app.data

import com.spendwise.app.domain.Budget
import com.spendwise.app.domain.Goal

// settings/{uid} writes (spec §3.5). validSettings allows only budget, goals, remindAt, remindOn and notif,
// so each save names just its own key and is merged with SetOptions.mergeFields (settings.js › saveSettings).

/** budget.js › saveBudget. "Remove budget" is Budget(): total null, no category limits. */
fun budgetFields(budget: Budget): Map<String, Any> = mapOf(
    "budget" to mapOf(
        "total" to budget.total?.let(::toFirestoreNumber),
        "cats" to budget.cats.mapValues { toFirestoreNumber(it.value) },
    ),
)

/** goals.js › saveGoals: the whole list, each goal with the web's five keys. */
fun goalsFields(goals: List<Goal>): Map<String, Any> = mapOf(
    "goals" to goals.map {
        mapOf("id" to it.id, "name" to it.name, "target" to toFirestoreNumber(it.target), "by" to it.by, "saved" to toFirestoreNumber(it.saved))
    },
)
