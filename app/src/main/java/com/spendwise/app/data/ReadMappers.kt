package com.spendwise.app.data

import com.spendwise.app.domain.Budget
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.Goal
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.I_OWE
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.Payment
import com.spendwise.app.domain.THEY_OWE
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.jsNumberOrZero

// Tolerant reads for income, debts, holdings and settings (spec §3.2–3.5, §3.7, §3.9), each matching the
// web reader named. Pure, so they're unit-tested.

private fun Map<String, Any?>.text(key: String) = this[key] as? String ?: ""

/** dashboard.js › loadIncome */
fun incomeFrom(id: String, data: Map<String, Any?>) = Income(
    id = id,
    amount = readAmount(data["amount"]),
    date = data.text("date"),
    source = data.text("source"),
    paymentType = data.text("paymentType"),
    bank = data.text("bank"),
    notes = data.text("notes"),
)

/** `paid` items are read with Number(), in helpers.js › debtPaid. */
private fun paymentsFrom(raw: Any?): List<Payment> = (raw as? List<*>).orEmpty().map { p ->
    val m = p as? Map<*, *>
    Payment(jsNumberOrZero(m?.get("amount")), m?.get("date") as? String ?: "")
}

/** dashboard.js › loadDebts: your own debt. `settled` may be true, "true" or 1 on old docs. */
fun ownDebtFrom(id: String, data: Map<String, Any?>): Debt {
    val settled = data["settled"]
    return Debt(
        id = id,
        amount = readAmount(data["amount"]),
        type = data.text("type"),
        person = data.text("person"),
        date = data.text("date"),
        notes = data.text("notes"),
        settled = settled == true || settled == "true" || (settled is Number && settled.toDouble() == 1.0),
        settledDate = data.text("settledDate"),
        paid = paymentsFrom(data["paid"]),
        claimDate = data.text("claimDate"),
        claimAmount = jsNumberOrZero(data["claimAmount"]).takeIf { it > 0 },
        email = data.text("withEmail"),
    )
}

/** helpers.js › sharedDebtView: a debt someone else recorded against your email, from your side. */
fun sharedDebtFrom(id: String, data: Map<String, Any?>) = Debt(
    id = id,
    amount = jsNumberOrZero(data["amount"]),
    type = if (data["type"] == THEY_OWE) I_OWE else THEY_OWE,
    person = (data["fromName"] as? String)?.takeIf { it.isNotEmpty() } ?: "Someone",
    date = data.text("date"),
    notes = data.text("notes"),
    settled = data["settled"] == true,
    settledDate = data.text("settledDate"),
    paid = paymentsFrom(data["paid"]),
    claimDate = data.text("claimDate"),
    claimAmount = jsNumberOrZero(data["claimAmount"]).takeIf { it > 0 },
    email = data.text("fromEmail"),
    shared = true,
)

/** invest-data.js › portfolio's "valued" test: a number ≥ 0 with a valueDate. */
fun holdingFrom(id: String, data: Map<String, Any?>): Holding {
    val value = (data["value"] as? Number)?.toDouble()
    val valueDate = (data["valueDate"] as? String)?.takeIf { it.isNotEmpty() }
    val valued = value != null && value >= 0 && valueDate != null
    return Holding(
        id = id,
        name = data["name"]?.toString().orEmpty(),
        kind = data.text("kind"),
        value = if (valued) value else null,
        valueDate = if (valued) valueDate else null,
        goalId = (data["goalId"] as? String)?.takeIf { it.isNotEmpty() },
    )
}

private val goalMonth = Regex("""^\d{4}-\d{2}$""")

/** budget.js › normalize and goals.js › getGoals. A missing doc reads as no budget and no goals. */
fun settingsFrom(data: Map<String, Any?>?): UserSettings {
    val b = data?.get("budget") as? Map<*, *>
    val total = (b?.get("total") as? Number)?.toDouble()?.takeIf { it > 0 }
    val cats = (b?.get("cats") as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
        if (k is String && v is Number) k to v.toDouble() else null
    }.toMap()
    val goals = (data?.get("goals") as? List<*>).orEmpty().mapNotNull { raw ->
        val g = raw as? Map<*, *> ?: return@mapNotNull null
        val id = g["id"] as? String
        val name = g["name"] as? String
        val target = (g["target"] as? Number)?.toDouble() ?: 0.0
        val by = g["by"] as? String
        if (id.isNullOrEmpty() || name.isNullOrEmpty() || !(target > 0) || by == null || !goalMonth.matches(by)) return@mapNotNull null
        val saved = (g["saved"] as? Number)?.toDouble() ?: 0.0
        Goal(id, name, target, by, if (saved > 0) saved else 0.0)
    }
    return UserSettings(Budget(total, cats), goals)
}
