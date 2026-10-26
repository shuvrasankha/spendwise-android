package com.spendwise.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.spendwise.app.ui.dashboard.SuggestState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.app.domain.Budget
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.EXPENSE_CATEGORIES
import com.spendwise.app.ui.invest.plain

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
    val total = remember { mutableStateOf(budget.total?.let(::plain).orEmpty()) }
    val cats = remember { mutableStateMapOf<String, String>().apply { budget.cats.forEach { (c, v) -> put(c, plain(v)) } } }
    // A suggestion fills the form for a look before saving
    LaunchedEffect(suggest?.fill) {
        val s = suggest?.fill ?: return@LaunchedEffect
        total.value = plain(s.total)
        cats.clear()
        s.cats.forEach { (c, v) -> cats[c] = plain(v) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Saved to your account. Home shows how much of it you've used.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onSuggest, enabled = suggest?.working != true, modifier = Modifier.fillMaxWidth()) {
                    Text(if (suggest?.working == true) "Working it out…" else "Suggest from my income")
                }
                suggest?.takeIf { !it.working }?.let { s ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.keeps) }
                            if (s.keeps.isNotEmpty()) append(" ")
                            append(s.text)
                        },
                        color = if (s.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                MoneyField("Monthly limit", total.value, currency) { total.value = it }
                Text("Limits by category (optional)", style = MaterialTheme.typography.titleSmall)
                EXPENSE_CATEGORIES.forEach { c -> MoneyField(c, cats[c].orEmpty(), currency) { cats[c] = it } }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(total.value, cats.toMap()) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (budget.total != null || budget.cats.isNotEmpty()) TextButton(onClick = onRemove) { Text("Remove budget") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun MoneyField(label: String, value: String, currency: Currency, placeholder: String? = null, onChange: (String) -> Unit) =
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = { Text(currency.symbol + " ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
