package com.spendwise.app.ui.budget

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.spendwise.app.domain.catLimits
import com.spendwise.app.domain.limitShare
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.format.formatWhole
import com.spendwise.app.ui.theme.Radius
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
import com.spendwise.app.domain.PLANS_MAX
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
    onSuggest: (plans: String) -> Unit,
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
    val plans = remember { mutableStateOf("") }
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
        Box(gutter) { MoneyField("Monthly limit", total.value, currency.symbol) { total.value = it } }

        // Optional help, below the limit it fills
        FieldLabel("Not sure? Let AI suggest")
        Box(gutter) {
            SwField(
                plans.value, { plans.value = it.take(PLANS_MAX) }, "Plans this month, e.g. Diwali, Goa trip",
                supportingText = "Optional. Sent to the AI as written, with your goal names. It also checks this month's festivals.",
            )
        }
        Box(gutter) { SwButton(if (suggest?.working == true) "Working it out…" else "Suggest from my income", { onSuggest(plans.value) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = suggest?.working != true, leading = SwIcons.Sparkles) }
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

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Limits by category")
            val split = catLimits(total.value, cats) { formatWhole(it, currency) }
            Text(
                split?.text ?: "Optional. Leave a category empty for no limit.",
                style = SwType.label, color = if (split?.over == true) c.negative else c.textMuted,
                modifier = gutter.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(gutter) {
            EXPENSE_CATEGORIES.forEachIndexed { i, cat ->
                if (i > 0) HorizontalDivider(color = c.line)
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(cat, style = SwType.body, color = c.text)
                        limitShare(cats[cat].orEmpty(), total.value)?.let { Text(it, style = SwType.caption, color = c.textMuted) }
                    }
                    LimitField(cats[cat].orEmpty(), currency.symbol, cat) { cats[cat] = it }
                }
            }
        }
    }
}

/** A compact amount box for one category: right-aligned, "No limit" when empty. */
@Composable
private fun LimitField(value: String, symbol: String, category: String, onChange: (String) -> Unit) {
    val c = SpendWiseTheme.colors
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value, onChange,
        Modifier.width(140.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = "$category limit" },
        textStyle = SwType.body.copy(color = c.text, textAlign = TextAlign.End),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(c.text),
        decorationBox = { inner ->
            Row(
                Modifier.heightIn(min = 44.dp).border(1.dp, if (focused) c.text else c.line, Radius.control).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(symbol, style = SwType.body, color = c.textMuted)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text("No limit", style = SwType.body, color = c.textFaint)
                    inner()
                }
            }
        },
    )
}
