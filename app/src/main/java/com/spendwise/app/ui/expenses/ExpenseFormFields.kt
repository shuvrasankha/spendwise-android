package com.spendwise.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.app.R
import com.spendwise.app.domain.CATEGORY_TILE_LABELS
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.PAYMENT_METHODS
import com.spendwise.app.domain.isCard
import com.spendwise.app.domain.todayStr
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.theme.SpendWiseTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The Add form's fields (spec §1.3), shared by Add and Edit. Stateless: the ViewModel owns [form]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormFields(form: ExpenseForm, currency: Currency, viewModel: ExpenseEditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = form.amount,
            onValueChange = viewModel::setAmount,
            label = { Text("Amount") },
            prefix = { Text(currency.symbol + " ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        DateField(form.date, viewModel::setDate)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CATEGORY_TILE_LABELS.forEach { (category, label) ->
                    FilterChip(
                        selected = form.category == category,
                        onClick = { viewModel.setCategory(category) },
                        label = { Text(label) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = form.description,
            onValueChange = viewModel::setDescription,
            label = { Text("Description") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Payment method", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAYMENT_METHODS.forEach { payment ->
                    FilterChip(selected = form.payment == payment, onClick = { viewModel.setPayment(payment) }, label = { Text(payment) })
                }
            }
        }
        if (isCard(form.payment)) {
            OutlinedTextField(
                value = form.cardName,
                onValueChange = viewModel::setCardName,
                label = { Text("Card name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = form.notes,
            onValueChange = viewModel::setNotes,
            label = { Text("Notes (optional)") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.tagDraft,
                onValueChange = viewModel::setTagDraft,
                label = { Text("Tags (optional)") },
                supportingText = { Text("Separate tags with a comma or space") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.commitTagDraft() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    form.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTag(tag) },
                            label = { Text("#$tag") },
                            trailingIcon = {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove tag $tag", Modifier.size(16.dp))
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Today / Yesterday chips plus a calendar, always as a local-calendar "YYYY-MM-DD" (spec §3.0). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateField(date: String, onDate: (String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val today = todayStr()
    val yesterday = LocalDate.parse(today).minusDays(1).toString()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Date", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = date == today, onClick = { onDate(today) }, label = { Text("Today") })
            FilterChip(selected = date == yesterday, onClick = { onDate(yesterday) }, label = { Text("Yesterday") })
            AssistChip(onClick = { picking = true }, label = { Text(if (date.isEmpty()) "Pick a date" else formatDate(date)) })
        }
    }

    if (picking) {
        // The picker works in UTC midnights; converting through UTC keeps the calendar day exact
        val initial = runCatching { LocalDate.parse(date) }.getOrNull()
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun FormMessageText(message: FormMessage?) {
    message ?: return
    Text(
        message.text,
        color = if (message.success) SpendWiseTheme.moneyColors.moneyIn else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
fun DeleteExpenseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this expense?") },
        text = { Text("It will be removed from SpendWise on all your devices.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
