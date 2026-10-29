package com.spendwise.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.spendwise.app.R

/** The icon for an expense category (EXPENSE_CATEGORIES); unknown or old categories get Other's. */
@DrawableRes
fun categoryIcon(category: String): Int = when (category) {
    "Food & Dining" -> R.drawable.ic_cat_food
    "Groceries" -> R.drawable.ic_cat_groceries
    "Transport" -> R.drawable.ic_cat_transport
    "Travel" -> R.drawable.ic_cat_travel
    "Shopping" -> R.drawable.ic_cat_shopping
    "Bills & Utilities" -> R.drawable.ic_cat_bills
    "Home & Rent" -> R.drawable.ic_cat_home
    "Health" -> R.drawable.ic_cat_health
    "Entertainment" -> R.drawable.ic_cat_entertainment
    "Education" -> R.drawable.ic_cat_education
    "Savings & Investments" -> R.drawable.ic_cat_savings
    else -> R.drawable.ic_cat_other
}

/** The icon for a payment method (PAYMENT_METHODS); Other and anything unknown get the three dots. */
@DrawableRes
fun paymentIcon(method: String): Int = when (method) {
    "UPI" -> R.drawable.ic_pay_upi
    "Cash" -> R.drawable.ic_pay_cash
    "Credit Card", "Debit Card" -> R.drawable.ic_pay_card
    "Net Banking" -> R.drawable.ic_pay_bank
    else -> R.drawable.ic_tab_more
}

/** A row's leading badge: [icon] in a flat circle. Decorative, since the row's text names the category too. */
@Composable
fun IconBadge(@DrawableRes icon: Int, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) = Box(
    Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
    contentAlignment = Alignment.Center,
) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}
