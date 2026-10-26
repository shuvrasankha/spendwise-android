package com.spendwise.app.domain

/** A web-shaped expense for tests: only what the case needs, the rest blank. */
fun ex(
    amount: Double,
    category: String,
    date: String,
    description: String = "x",
    repeat: String? = null,
    emiTotal: Int? = null,
    emiPaidBefore: Int? = null,
    holdingId: String? = null,
    id: String = "$date-$description-$amount",
    payment: String = "UPI",
) = Expense(id, amount, category, date, payment, "", description, "", emptyList(), repeat, emiTotal, emiPaidBefore, holdingId)

const val FOOD = "Food & Dining"
const val INVEST = "Savings & Investments"
