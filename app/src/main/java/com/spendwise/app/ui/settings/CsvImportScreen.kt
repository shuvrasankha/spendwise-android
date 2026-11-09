package com.spendwise.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.spendwise.app.ui.components.SwField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.CsvTx
import com.spendwise.app.domain.Currency
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.StatGrid
import com.spendwise.app.ui.components.Stat
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** Import from a bank statement (csv-upload.js): pick a CSV, PDF or Excel file, the AI sorts it, you check the rows, then save. */
@Composable
fun CsvImportScreen(onDone: () -> Unit, vm: CsvImportViewModel = hiltViewModel()) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val consent by vm.consentAsked.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::pick)
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)

    Scaffold(contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SubPageTopBar("Import a statement", onDone)
            val body = Modifier.fillMaxSize().padding(Spacing.gutter)
            when (val p = phase) {
                is CsvPhase.Upload -> Column(body, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("AI sorts a bank statement into expenses and income, and you check every row before saving.", style = SwType.body, color = c.textMuted)
                    SwButton(
                        "Choose a file", { pick.launch(STATEMENT_TYPES) },
                        Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, leading = SwIcons.Upload,
                    )
                    Text(p.fileName ?: "No file selected", style = SwType.label, color = c.textMuted)
                    Text("CSV, PDF or Excel (.xlsx). Up to 200 rows; a PDF of about a month.", style = SwType.caption, color = c.textFaint)
                    // Reset when another file is picked; the password is never saved
                    var password by rememberSaveable(p.fileName) { mutableStateOf("") }
                    if (p.needsPassword) {
                        Text("This PDF is locked. Enter the password your bank gave for it.", style = SwType.body, color = c.text)
                        SwField(
                            password, { password = it }, "PDF password", leading = SwIcons.Lock, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        )
                    }
                    p.note?.let { Text(it, style = SwType.label, color = c.negative) }
                    SwButton(
                        "Read with AI", { vm.parse(password.takeIf { p.needsPassword }) }, Modifier.fillMaxWidth(),
                        enabled = p.fileName != null && (!p.needsPassword || password.isNotEmpty()),
                    )
                }
                is CsvPhase.Processing -> Busy(body, p.step)
                CsvPhase.Saving -> Busy(body, "Saving…")
                is CsvPhase.Preview -> Preview(p, currency, vm)
                is CsvPhase.Done -> Column(body, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(p.text, style = SwType.body, color = c.positive)
                    SwButton("Done", onDone)
                }
                is CsvPhase.Failed -> Column(body, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(p.text, style = SwType.body, color = c.negative)
                    SwButton("Try again", { vm.restart() }, style = ButtonStyle.Secondary)
                }
            }
        }
    }

    if (consent) {
        AlertDialog(
            onDismissRequest = { vm.answerConsent(false) },
            title = { Text("Statement Upload Consent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your statement will be sent to an AI service (Cloudflare Workers AI) for automatic column detection and transaction extraction.")
                    Text("What is sent: The statement's rows from the table heading on, including all columns (max 200 rows). A PDF or Excel file is read on your phone and only its text is sent.")
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
    CircularProgressIndicator(color = SpendWiseTheme.colors.text)
    Text(text, style = SwType.body, color = SpendWiseTheme.colors.textMuted)
}

@Composable
private fun Preview(p: CsvPhase.Preview, currency: Currency, vm: CsvImportViewModel) {
    val c = SpendWiseTheme.colors
    val expenses = p.rows.filter { it.type != "income" }
    val income = p.rows.filter { it.type == "income" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.section), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            StatGrid(
                listOf(
                    Stat("Expenses", formatAmount(expenses.sumOf { it.amount }, currency), SwIcons.MoneyOut, sub = "${expenses.size} found"),
                    Stat("Income", formatAmount(income.sumOf { it.amount }, currency), SwIcons.MoneyIn, c.positive, sub = "${income.size} found"),
                ),
            )
        }
        item {
            val all = p.selected.size == p.rows.size
            Row(
                Modifier.fillMaxWidth().toggleable(all, role = Role.Checkbox) { vm.toggleAll(it) }.padding(horizontal = Spacing.gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tick(all)
                Text("Select all", style = SwType.bodyStrong, color = c.text)
            }
        }
        // Selection is by row number in the parsed file, so that is the row's identity too
        itemsIndexed(p.rows, key = { i, _ -> "tx-$i" }) { i, tx -> TxRow(tx, i in p.selected, currency, { vm.flipType(i) }) { vm.toggle(i) } }
        item {
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                p.note?.let { Text(it, style = SwType.label, color = c.negative) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwButton("Back", { vm.restart() }, style = ButtonStyle.Secondary)
                    SwButton("Import ${p.selected.size}", vm::import, Modifier.weight(1f))
                }
            }
        }
    }
}

/** A selected state is inverted: `text` fill with a `bg` tick. */
@Composable
private fun Tick(on: Boolean) {
    val c = SpendWiseTheme.colors
    Box(
        Modifier.size(22.dp).clip(Radius.segment).then(if (on) Modifier.background(c.text) else Modifier.border(1.dp, c.line, Radius.segment)),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(SwIcons.Check, null, Modifier.size(16.dp), tint = c.bg) }
}

@Composable
private fun TxRow(tx: CsvTx, checked: Boolean, currency: Currency, onFlip: () -> Unit, onToggle: () -> Unit) {
    val c = SpendWiseTheme.colors
    val isIncome = tx.type == "income"
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { onToggle() }.padding(horizontal = Spacing.gutter, vertical = Spacing.rowV),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tick(checked)
        Column(Modifier.weight(1f)) {
            Text(tx.description, style = SwType.bodyStrong, color = c.text)
            Text(
                listOf(formatDate(tx.date), if (isIncome) "Income" else "Expense · ${tx.category}", tx.payment).joinToString(" · "),
                style = SwType.caption, color = c.textMuted,
            )
            TextButton(onFlip, contentPadding = PaddingValues(0.dp)) {
                Text(if (isIncome) "Make it an expense" else "Make it income", style = SwType.label, color = c.text)
            }
        }
        Text((if (isIncome) "+" else "") + formatAmount(tx.amount, currency), style = SwType.bodyStrong, color = if (isIncome) c.positive else c.text)
    }
}

/** CSV, PDF and Excel; "application/vnd.ms-excel" and text/plain too, as some apps label a CSV so. */
private val STATEMENT_TYPES = arrayOf(
    "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/pdf",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)
