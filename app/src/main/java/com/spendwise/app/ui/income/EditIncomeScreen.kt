package com.spendwise.app.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.INCOME_PAYMENT_TYPES
import com.spendwise.app.domain.ONLINE
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.DateChips
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.SegmentedControl
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.LocalDate

/** Edit one income entry (spec §1.4, §1.6, §2.5): the income fields, Save, and Delete with confirmation. */
@Composable
fun EditIncomeScreen(onDone: () -> Unit, editor: IncomeEditorViewModel = hiltViewModel()) {
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)

    Scaffold(contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            SubPageTopBar("Edit income", onDone)
            val f = state.form
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.text) }
                state.missing -> Text(
                    "This income entry was deleted, is waiting to sync, or couldn't be loaded.",
                    style = SwType.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter),
                )
                else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(gutter) {
                        SwField(f.amount, editor::setAmount, "Amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    FieldLabel("Date")
                    DateChips(f.date, LocalDate.now(), editor::setDate)
                    Box(gutter) { SwField(f.source, editor::setSource, "From") }
                    FieldLabel("Received as")
                    Box(gutter) {
                        SegmentedControl(INCOME_PAYMENT_TYPES, INCOME_PAYMENT_TYPES.indexOf(f.paymentType).coerceAtLeast(0), { editor.setPaymentType(INCOME_PAYMENT_TYPES[it]) })
                    }
                    if (f.paymentType == ONLINE) Box(gutter) { SwField(f.bank, editor::setBank, "Bank or wallet") }
                    Box(gutter) { SwField(f.notes, editor::setNotes, "Notes (optional)", singleLine = false) }
                    state.message?.let { Text(it.text, style = SwType.label, color = if (it.success) c.positive else c.negative, modifier = gutter) }
                    Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SwButton("Delete", editor::askDelete, style = ButtonStyle.Danger)
                        SwButton("Save", editor::save, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = editor::cancelDelete,
            title = { Text("Delete this income entry?") },
            text = { Text("It will be removed from SpendWise on all your devices.") },
            confirmButton = { TextButton(onClick = editor::confirmDelete) { Text("Delete", color = c.negative) } },
            dismissButton = { TextButton(onClick = editor::cancelDelete) { Text("Cancel") } },
        )
    }
}
