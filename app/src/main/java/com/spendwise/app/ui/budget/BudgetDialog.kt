package com.spendwise.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import com.spendwise.app.domain.Budget
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.EXPENSE_CATEGORIES
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.MoneyField
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwSheet
import com.spendwise.app.ui.dashboard.SuggestState
import com.spendwise.app.ui.invest.plain
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** "Monthly budget" (budget.js): a monthly limit, optional limits by category, and "Suggest from my income". */
@Composable
fun BudgetDialog(
    budget: Budget,
    currency: Currency,
    error: String?,
    suggest: SuggestState?,
    onSuggest: () -> Unit,
    onSave: (total: String, cats: Map<String, String>) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SpendWiseTheme.colors
    val total = remember { mutableStateOf(budget.total?.let(::plain).orEmpty()) }
    val cats = remember { mutableStateMapOf<String, String>().apply { budget.cats.forEach { (c, v) -> put(c, plain(v)) } } }
    // A suggestion fills the form for a look before saving
    LaunchedEffect(suggest?.fill) {
        val s = suggest?.fill ?: return@LaunchedEffect
        total.value = plain(s.total)
        cats.clear()
        s.cats.forEach { (c, v) -> cats[c] = plain(v) }
    }
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    SwSheet(
        "Monthly budget", onDismiss,
        footer = {
            error?.let { Text(it, style = SwType.label, color = c.negative, modifier = gutter) }
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (budget.total != null || budget.cats.isNotEmpty()) SwButton("Remove", onRemove, style = ButtonStyle.Danger)
                SwButton("Save", { onSave(total.value, cats.toMap()) }, Modifier.weight(1f))
            }
        },
    ) {
        Text("Saved to your account. Home shows how much of it you've used.", style = SwType.label, color = c.textMuted, modifier = gutter)
        Box(gutter) { SwButton(if (suggest?.working == true) "Working it out…" else "Suggest from my income", onSuggest, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = suggest?.working != true, leading = SwIcons.Sparkles) }
        suggest?.takeIf { !it.working }?.let { s ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.keeps) }
                    if (s.keeps.isNotEmpty()) append(" ")
                    append(s.text)
                },
                style = SwType.body, color = if (s.error) c.negative else c.text,
                modifier = gutter.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Box(gutter) { MoneyField("Monthly limit", total.value, currency.symbol) { total.value = it } }
        FieldLabel("Limits by category (optional)")
        Column(gutter, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EXPENSE_CATEGORIES.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(cat, style = SwType.body, color = c.text, modifier = Modifier.weight(1f))
                    Box(Modifier.weight(1f)) {
                        com.spendwise.app.ui.components.SwField(
                            cats[cat].orEmpty(), { cats[cat] = it }, "No limit", prefix = currency.symbol,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        )
                    }
                }
            }
        }
    }
}
