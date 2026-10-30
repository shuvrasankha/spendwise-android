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
