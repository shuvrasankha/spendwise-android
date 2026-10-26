package com.spendwise.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.R
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.CsvTx
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.format.formatMoney
import com.spendwise.app.ui.theme.SpendWiseTheme

/** Import from a bank statement (csv-upload.js): pick a CSV, the AI sorts it, you check the rows, then save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(onDone: () -> Unit, vm: CsvImportViewModel = hiltViewModel()) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val consent by vm.consentAsked.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::pick)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Import from a bank statement") },
                windowInsets = WindowInsets(0),
                navigationIcon = { IconButton(onClick = onDone) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        when (val p = phase) {
            is CsvPhase.Upload -> Column(body, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("A CSV file: AI sorts it into expenses and income, and you check it before saving. Up to 200 rows and 512 KB.")
                OutlinedButton(onClick = { pick.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")) }) {
                    Text("Choose a CSV file")
                }
                Text(p.fileName ?: "No file selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                p.note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::parse, enabled = p.fileName != null) { Text("Read with AI") }
            }
            is CsvPhase.Processing -> Busy(body, p.step)
            CsvPhase.Saving -> Busy(body, "Saving…")
            is CsvPhase.Preview -> Preview(p, currency, vm, Modifier.fillMaxSize().padding(padding))
            is CsvPhase.Done -> Column(body, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(p.text, color = SpendWiseTheme.moneyColors.moneyIn)
                Button(onClick = onDone) { Text("Done") }
            }
            is CsvPhase.Failed -> Column(body, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(p.text, color = MaterialTheme.colorScheme.error)
                Button(onClick = { vm.restart() }) { Text("Try again") }
            }
        }
    }

    if (consent) {
        AlertDialog(
            onDismissRequest = { vm.answerConsent(false) },
            title = { Text("CSV Upload Consent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your CSV file will be sent to an AI service (Cloudflare Workers AI) for automatic column detection and transaction extraction.")
                    Text("What is sent: The raw CSV content including all columns and rows (max 200 rows).")
                    Text("What happens: AI analyzes the data to detect columns, categories, and dates, then returns structured transaction data.")
                    Text("Your data is processed securely and is not stored by the AI service. You can review and edit all detected transactions before importing.")
                }
            },
            confirmButton = { TextButton(onClick = { vm.answerConsent(true) }) { Text("I Understand & Continue") } },
            dismissButton = { TextButton(onClick = { vm.answerConsent(false) }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Busy(modifier: Modifier, text: String) = Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    CircularProgressIndicator()
    Text(text)
}

@Composable
private fun Preview(p: CsvPhase.Preview, currency: Currency, vm: CsvImportViewModel, modifier: Modifier) {
    val expenses = p.rows.filter { it.type != "income" }
    val income = p.rows.filter { it.type == "income" }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("${expenses.size} expense${if (expenses.size != 1) "s" else ""} (${formatMoney(expenses.sumOf { it.amount }, currency)})")
            Text("${income.size} income (${formatMoney(income.sumOf { it.amount }, currency)})", color = SpendWiseTheme.moneyColors.moneyIn)
        }
        item {
            val all = p.selected.size == p.rows.size
            Row(Modifier.fillMaxWidth().toggleable(all, role = Role.Checkbox) { vm.toggleAll(it) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = all, onCheckedChange = null)
                Text("Select all", modifier = Modifier.padding(start = 8.dp))
            }
        }
        itemsIndexed(p.rows) { i, tx -> TxRow(tx, i in p.selected, currency) { vm.toggle(i) } }
        item {
            p.note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { vm.restart() }, modifier = Modifier.weight(1f)) { Text("Back") }
                Button(onClick = vm::import, modifier = Modifier.weight(1f)) { Text("Import ${p.selected.size}") }
            }
        }
    }
}

@Composable
private fun TxRow(tx: CsvTx, checked: Boolean, currency: Currency, onToggle: () -> Unit) {
    val isIncome = tx.type == "income"
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(tx.description)
            Text(
                listOf(formatDate(tx.date), if (isIncome) "Income" else "Expense · ${tx.category}", tx.payment).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatMoney(tx.amount, currency), color = if (isIncome) SpendWiseTheme.moneyColors.moneyIn else MaterialTheme.colorScheme.onSurface)
    }
}
