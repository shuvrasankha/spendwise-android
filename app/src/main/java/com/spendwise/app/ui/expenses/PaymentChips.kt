package com.spendwise.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendwise.app.domain.Card
import com.spendwise.app.domain.PAYMENT_METHODS
import com.spendwise.app.domain.cardKey
import com.spendwise.app.domain.isCard
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.cards.cardColor
import com.spendwise.app.ui.components.SwField

/**
 * "Paid with": the methods, with each saved card after Cash ("HDFC Millennia · Credit"). A saved card sets the type
 * and name in one tap; Credit Card or Debit Card on its own is for a new card, whose name is typed below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentChips(
    payment: String,
    cardName: String,
    cards: List<Card>,
    colours: Map<String, String>,
    onPayment: (String) -> Unit,
    onCard: (Card) -> Unit,
    onCardName: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    // Typing a new name keeps the field open, even when the name so far matches a saved card ("HDFC" before "HDFC Regalia")
    var typing by rememberSaveable { mutableStateOf(false) }
    val picked = if (typing) null else cards.firstOrNull { isCard(payment) && it.key == cardKey(cardName, payment) }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PAYMENT_METHODS.forEach { m ->
            if (isCard(m)) {
                SwChip(if (cards.isEmpty()) m else "New ${m.lowercase()}", payment == m && picked == null, {
                    typing = true
                    onPayment(m)
                    onCardName("")
                })
            } else {
                SwChip(m, payment == m, { typing = false; onPayment(m) })
            }
            if (m == "Cash") cards.forEach { c -> SwChip("${c.name} · ${c.short}", picked == c, { typing = false; onCard(c) }, dot = cardColor(colours[c.key])) }
        }
    }
    if (isCard(payment) && picked == null) {
        androidx.compose.foundation.layout.Box(fieldModifier) { SwField(cardName, onCardName, if (cards.isEmpty()) "Card name (optional)" else "New card's name (optional)") }
    }
}
