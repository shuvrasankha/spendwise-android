package com.spendwise.app.ui.income

import com.spendwise.app.ui.components.Panel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.recentMeta
import com.spendwise.app.ui.expenses.FormMessageText
import com.spendwise.app.ui.format.formatMoney
import com.spendwise.app.ui.format.plural
import com.spendwise.app.ui.theme.SpendWiseTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Income (spec §1.4): this month so far, the add form, and the last 8 entries. */
@Composable
fun IncomeScreen(onEdit: (String) -> Unit, onAllIncome: () -> Unit, income: IncomeViewModel = hiltViewModel()) {
    val page by income.page.collectAsStateWithLifecycle()
    val form by income.form.collectAsStateWithLifecycle()
    val message by income.message.collectAsStateWithLifecycle()
    val currency by income.currency.collectAsStateWithLifecycle()
    val ready = page as? IncomePage.Ready

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Income", style = MaterialTheme.typography.headlineSmall) }
        item { Panel { Hero(page, currency) } }
        item {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Add income", style = MaterialTheme.typography.titleMedium)
                    IncomeFormFields(form, income, ready?.sources.orEmpty(), ready?.banks.orEmpty())
                    FormMessageText(message)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = income::clear, modifier = Modifier.weight(1f)) { Text("Clear") }
                        Button(onClick = income::add, modifier = Modifier.weight(1f)) { Text("Add income") }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onAllIncome) { Text("All income") }
            }
        }
        when (val p = page) {
            IncomePage.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is IncomePage.Failed -> item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(p.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = income::retry) { Text("Try again") }
                }
            }
            is IncomePage.Ready -> {
                if (p.recent.isEmpty()) item {
                    Text("No income yet. Add your first one above.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(p.recent, key = { it.id }) { IncomeRow(it, currency) { onEdit(it.id) } }
            }
        }
    }
}

@Composable
private fun Hero(page: IncomePage, currency: Currency) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.US)
        Text("$month so far", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val s = (page as? IncomePage.Ready)?.summary
        Text(
            if (s == null) "—" else formatMoney(s.monthTotal, currency),
            style = MaterialTheme.typography.displaySmall,
            color = SpendWiseTheme.moneyColors.moneyIn,
        )
        if (s != null) {
            Text(
                "${plural(s.monthCount, "entry", "entries")} · ${formatMoney(s.yearTotal, currency)} this year",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IncomeRow(i: Income, currency: Currency, onEdit: () -> Unit) {
    // An entry waiting to sync has no server record yet, so it can't be edited
    Panel(onClick = onEdit.takeUnless { i.pending }, onClickLabel = "Edit") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(i.source.ifEmpty { "Income" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(recentMeta(i, LocalDate.now()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (i.notes.isNotEmpty()) {
                    Text(i.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            // "+" says it's money in, as well as the colour
            Text("+" + formatMoney(i.amount, currency), color = SpendWiseTheme.moneyColors.moneyIn)
        }
    }
}
