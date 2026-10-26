package com.spendwise.app.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.R
import com.spendwise.app.domain.INCOME_PAYMENT_TYPES
import com.spendwise.app.domain.ONLINE
import com.spendwise.app.ui.expenses.FormMessageText
import com.spendwise.app.ui.expenses.PickDateDialog
import com.spendwise.app.ui.format.formatDate

/** Edit one income entry (spec §1.6, §2.5): amount, date, source, payment type, bank, notes; Save, and Delete with confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditIncomeScreen(onDone: () -> Unit, editor: IncomeEditorViewModel = hiltViewModel()) {
    val state by editor.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Edit income") },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val f = state.form
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> Text(
                    "This income entry was deleted, is waiting to sync, or couldn't be loaded.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        OutlinedTextField(
                            f.amount, editor::setAmount, label = { Text("Amount") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Date", style = MaterialTheme.typography.titleSmall)
                            AssistChip(onClick = { picking = true }, label = { Text(if (f.date.isEmpty()) "Pick a date" else formatDate(f.date)) })
                        }
                    }
                    item {
                        OutlinedTextField(f.source, editor::setSource, label = { Text("Source / From") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Payment type", style = MaterialTheme.typography.titleSmall)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                INCOME_PAYMENT_TYPES.forEachIndexed { i, type ->
                                    SegmentedButton(
                                        selected = f.paymentType == type,
                                        onClick = { editor.setPaymentType(type) },
                                        shape = SegmentedButtonDefaults.itemShape(i, INCOME_PAYMENT_TYPES.size),
                                    ) { Text(type) }
                                }
                            }
                        }
                    }
                    if (f.paymentType == ONLINE) item {
                        OutlinedTextField(f.bank, editor::setBank, label = { Text("Bank / Wallet") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(f.notes, editor::setNotes, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    }
                    item { FormMessageText(state.message) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = editor::askDelete, modifier = Modifier.weight(1f)) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                            Button(onClick = editor::save, modifier = Modifier.weight(1f)) { Text("Save") }
                        }
                    }
                }
            }
        }
    }

    if (picking) PickDateDialog(state.form.date, editor::setDate, onDismiss = { picking = false })
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = editor::cancelDelete,
            title = { Text("Delete this income entry?") },
            text = { Text("It will be removed from SpendWise on all your devices.") },
            confirmButton = { TextButton(onClick = editor::confirmDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = editor::cancelDelete) { Text("Cancel") } },
        )
    }
}
