package com.spendwise.app.ui.settings

import com.spendwise.app.ui.components.Avatar
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.data.prefs.ThemeMode
import com.spendwise.app.domain.CURRENCIES
import com.spendwise.app.ui.dashboard.PlanDialogs
import com.spendwise.app.ui.dashboard.PlansViewModel

/** Settings (spec §1.9), in the web's order: Account, Display, Money, Notifications, then Log out; plus Delete account (§8.2). */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel(), plans: PlansViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    var pickingCurrency by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.turnReminderOn(it) }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))

        Group("Account")
        ListItem(
            leadingContent = { Avatar(state.name.ifEmpty { state.email }) },
            headlineContent = { Text(state.name.ifEmpty { state.email }) },
            supportingContent = if (state.name.isEmpty()) null else ({ Text(state.email) }),
        )

        Group("Display")
        Text("Appearance", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Muted("Light, dark, or follow your device")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = state.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) { Text(mode.label) }
            }
        }
        ListItem(
            headlineContent = { Text("Currency") },
            supportingContent = { Text("${state.currency.symbol} ${state.currency.code} — ${state.currency.name} · used for every amount in the app") },
            modifier = Modifier.clickable { pickingCurrency = true },
        )

        Group("Money")
        ListItem(
            headlineContent = { Text("Monthly budget") },
            supportingContent = { Text("An overall limit, and limits per category") },
            modifier = Modifier.clickable(onClick = plans::openBudget),
        )
        val budgetNotice by plans.notice.collectAsStateWithLifecycle()
        budgetNotice?.let { Muted(it.text) }

        Group("Notifications")
        ListItem(
            headlineContent = { Text("Daily reminder") },
            trailingContent = {
                Switch(
                    checked = state.reminderOn,
                    onCheckedChange = { on ->
                        when {
                            !on -> viewModel.turnReminderOff()
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> viewModel.turnReminderOn(true)
                        }
                    },
                    modifier = Modifier.semantics { stateDescription = if (state.reminderOn) "On" else "Off" },
                )
            },
        )
        ListItem(
            headlineContent = { Text("Reminder time") },
            trailingContent = { Text(state.reminderAt, style = MaterialTheme.typography.titleMedium) },
            modifier = Modifier.clickable { pickingTime = true },
        )
        state.reminderNote?.let { Muted(it) }

        Group("App")
        OutlinedButton(onClick = viewModel::logOut, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Log out") }
        TextButton(onClick = viewModel::askDelete, modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)) {
            Text("Delete account", color = MaterialTheme.colorScheme.error)
        }
    }

    if (pickingCurrency) CurrencyDialog(state.currency.code, viewModel::setCurrency) { pickingCurrency = false }
    if (pickingTime) TimeDialog(state.reminderAt, viewModel::setReminderTime) { pickingTime = false }
    PlanDialogs(plans, state.currency)
    DeletionDialog(deletion, viewModel)
}

@Composable
private fun Group(title: String) = Text(
    title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 4.dp),
)

@Composable
private fun Muted(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp),
)

@Composable
private fun CurrencyDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Currency") },
    text = {
        LazyColumn {
            items(CURRENCIES, key = { it.code }) { currency ->
                val selected = currency.code == current
                ListItem(
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    headlineContent = { Text("${currency.symbol} ${currency.code} — ${currency.name}") },
                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                        onPick(currency.code)
                        onDismiss()
                    },
                )
            }
        }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(at: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val (h, m) = at.split(":").map { it.toIntOrNull() ?: 0 }
    val time = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(time) },
        confirmButton = {
            TextButton(onClick = {
                onPick("%02d:%02d".format(time.hour, time.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeletionDialog(d: Deletion, vm: SettingsViewModel) {
    val context = LocalContext.current
    val error = MaterialTheme.colorScheme.error
    when (d) {
        Deletion.None -> Unit
        is Deletion.Confirm -> AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This permanently deletes your SpendWise account and everything in it: expenses, income, debts, investments, budget and goals, on every device, including the website.")
                    Text("Debts you shared with other people disappear for them too. Debts others shared with you stay with them.")
                    Text("This can't be undone. Type $DELETE_WORD to confirm.")
                    OutlinedTextField(
                        value = d.typed,
                        onValueChange = vm::typeConfirm,
                        singleLine = true,
                        label = { Text("Type $DELETE_WORD") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete, enabled = d.typed.trim() == DELETE_WORD) { Text("Continue", color = if (d.typed.trim() == DELETE_WORD) error else MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancel") } },
        )
        is Deletion.Reauth -> {
            var password by rememberSaveable { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { if (!d.busy) vm.cancelDelete() },
                title = { Text("Confirm it's you") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (d.usesPassword) "Enter your password. Deleting starts as soon as it's confirmed."
                            else "Choose the Google account you signed in with. Deleting starts as soon as it's confirmed.",
                        )
                        if (d.usesPassword) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                enabled = !d.busy,
                            )
                        }
                        d.error?.let { Text(it, color = error) }
                        if (d.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { if (d.usesPassword) vm.reauthWithPassword(password) else vm.reauthWithGoogle(context) },
                        enabled = !d.busy && (!d.usesPassword || password.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(containerColor = error, contentColor = MaterialTheme.colorScheme.onError),
                    ) { Text(if (d.usesPassword) "Delete account" else "Continue with Google") }
                },
                dismissButton = { TextButton(onClick = vm::cancelDelete, enabled = !d.busy) { Text("Cancel") } },
            )
        }
        is Deletion.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Deleting your account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Removing your ${d.label}… (${d.step} of ${d.of})")
                    LinearProgressIndicator(progress = { d.step.toFloat() / d.of }, modifier = Modifier.fillMaxWidth())
                    Text("Keep the app open until it's done.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {},
        )
        is Deletion.Failed -> AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Account not fully deleted") },
            text = { Text(d.message) },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Try again") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Later") } },
        )
    }
}

private val ThemeMode.label
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
