package com.spendwise.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.EmiPlan
import com.spendwise.app.domain.ExpenseFigures
import com.spendwise.app.domain.emiHeader
import com.spendwise.app.domain.meta
import com.spendwise.app.domain.percent
import com.spendwise.app.ui.format.formatMoney
import com.spendwise.app.ui.format.formatWhole
import java.time.LocalDate

private fun entries(n: Int) = "$n ${if (n == 1) "entry" else "entries"}"

/** The Add page's four figures (dashboard.js › updateExpenseCards, spec §6.4). */
@Composable
fun ExpenseSummary(f: ExpenseFigures, currency: Currency) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure("This Month", formatMoney(f.month, currency), entries(f.monthCount), Modifier.weight(1f))
            Figure("This Year", formatMoney(f.year, currency), entries(f.yearCount), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure("Total Expenses", formatMoney(f.all, currency), entries(f.allCount), Modifier.weight(1f))
            Figure("Daily Average", formatMoney(f.perDay, currency), "avg / day this month", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** EMIs: each plan's payments so far, what's left and when it ends (dashboard.js › renderEmis). */
@Composable
fun EmiCard(plans: List<EmiPlan>, currency: Currency) {
    val whole = { n: Double -> formatWhole(n, currency) }
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Text("EMIs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(emiHeader(plans, whole), style = MaterialTheme.typography.bodyMedium)
        }
        plans.forEach { p ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(p.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${whole(p.monthly)}/mo", style = MaterialTheme.typography.bodyLarge)
                }
                LinearProgressIndicator(
                    progress = { p.percent / 100f },
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${p.title}: ${p.paid} of ${p.total} payments" },
                )
                Text(p.meta(today, whole), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
