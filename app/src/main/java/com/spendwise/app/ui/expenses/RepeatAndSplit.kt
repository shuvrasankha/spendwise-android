package com.spendwise.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.app.R
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.MAX_SPLIT_PEOPLE
import com.spendwise.app.domain.splitSummary
import com.spendwise.app.ui.components.PersonSuggestions
import com.spendwise.app.ui.format.formatMoney

/** "Repeats every month", and on Add the EMI's payments (spec §2.3, §6.5). */
@Composable
fun RepeatFields(form: ExpenseForm, adding: Boolean, viewModel: ExpenseEditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Repeats every month", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (adding) "Rent, subscriptions, SIPs, EMIs: the Dashboard offers it again when it's due"
                    else "Rent, subscriptions, SIPs: the Dashboard offers it again when it's due",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = form.repeat,
                onCheckedChange = viewModel::setRepeat,
                modifier = Modifier.semantics { stateDescription = if (form.repeat) "Repeats" else "Doesn't repeat" },
            )
        }
        if (adding && form.repeat) {
            Text("EMI (optional)", style = MaterialTheme.typography.titleSmall)
            Text(
                "For a loan or a no-cost EMI, enter how many payments it has. It stops by itself after the last one. Leave empty for rent or a subscription.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.emiTotal,
                    onValueChange = viewModel::setEmiTotal,
                    label = { Text("Total payments") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.emiBefore,
                    onValueChange = viewModel::setEmiBefore,
                    label = { Text("Already paid") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** "Split with others" on Add (split.js): your share is the expense; each person owes you theirs. */
@Composable
fun SplitFields(state: EditorState, currency: Currency, viewModel: ExpenseEditorViewModel) {
    val form = state.form
    val (hint, note) = splitSummary(form.split, form.amount) { formatMoney(it, currency) }
    Collapsible("Split with others", hint) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        form.split.forEachIndexed { i, row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { viewModel.setSplitPerson(i, row.copy(name = it.take(100))) },
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PersonSuggestions(row.name, row.email, state.known) { viewModel.pickSplitPerson(i, it) }
                    if (state.canShare) {
                        OutlinedTextField(
                            value = row.email,
                            onValueChange = { viewModel.setSplitPerson(i, row.copy(email = it.take(254))) },
                            label = { Text("Email (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                IconButton(onClick = { viewModel.removeSplitPerson(i) }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove person ${i + 1}", Modifier.size(18.dp))
                }
            }
        }
        if (form.split.size < MAX_SPLIT_PEOPLE) {
            OutlinedButton(onClick = viewModel::addSplitPerson) { Text("Add person") }
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.canShare) {
            Text(
                "Add someone's email and, if they use SpendWise with it, the amount shows up in their Debts too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } }
}
