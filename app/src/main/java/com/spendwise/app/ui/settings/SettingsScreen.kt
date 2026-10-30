package com.spendwise.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.data.prefs.ThemeMode
import com.spendwise.app.data.prefs.WIDGET_TRANSPARENCY_LEVELS
import com.spendwise.app.domain.CURRENCIES
import com.spendwise.app.domain.PAYMENT_METHODS
import com.spendwise.app.ui.components.Avatar
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.SettingsGroup
import com.spendwise.app.ui.components.SettingsRow
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.SwSheet
import com.spendwise.app.ui.components.Trailing
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.lock.canLock
import com.spendwise.app.ui.lock.confirmIdentity
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** Settings (spec §1.9): Display, Notifications, Security and About, then Log out; plus Delete account (§8.2). */
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    val defaultPayment by viewModel.defaultPayment.collectAsStateWithLifecycle()
    val transparency by viewModel.widgetTransparency.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.turnReminderOn(it) }
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)

    Scaffold(contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SubPageTopBar("Settings", onBack)

            Row(gutter, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(state.name.ifEmpty { state.email }, size = 56.dp, photoUrl = state.photoUrl)
                Column(Modifier.weight(1f)) {
                    Text(state.name.ifEmpty { state.email }, style = SwType.heading, color = c.text)
                    if (state.name.isNotEmpty()) Text(state.email, style = SwType.label, color = c.textMuted)
                }
            }

            SettingsGroup("Display") {
                SettingsRow("Appearance", trailing = Trailing.Value(state.themeMode.label), onClick = { picking = "theme" })
                SettingsRow("Widget transparency", trailing = Trailing.Value("$transparency%"), divider = true, onClick = { picking = "widget" })
                SettingsRow("Currency", trailing = Trailing.Value("${state.currency.symbol} ${state.currency.code}"), divider = true, onClick = { picking = "currency" })
                SettingsRow("Default payment", trailing = Trailing.Value(defaultPayment), divider = true, onClick = { picking = "payment" })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsGroup("Notifications") {
                    SettingsRow("Daily reminder", trailing = Trailing.Switch(state.reminderOn) { on ->
                        when {
                            !on -> viewModel.turnReminderOff()
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> viewModel.turnReminderOn(true)
                        }
                    })
                    SettingsRow("Reminder time", trailing = Trailing.Value(state.reminderAt), divider = true, onClick = { picking = "time" })
                }
                state.reminderNote?.let { Text(it, style = SwType.label, color = c.textMuted, modifier = gutter) }
            }

            val activity = LocalContext.current as FragmentActivity
            if (remember { canLock(activity) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsGroup("Security") {
                        SettingsRow("App lock", trailing = Trailing.Switch(state.lockOn) { on -> activity.confirmIdentity { ok -> if (ok) viewModel.setLock(on) } })
                    }
                    Text(
                        "Asks for your fingerprint, face or screen lock when SpendWise opens, and after 5 minutes away. This device only.",
                        style = SwType.label, color = c.textMuted, modifier = gutter,
                    )
                }
            }

            SettingsGroup("About") {
                SettingsRow("App tour", onClick = viewModel::replayTour)
                SettingsRow("Privacy policy", divider = true, onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) })
            }

            Column(gutter, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SwButton("Log out", viewModel::logOut, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, leading = SwIcons.LogOut)
                SwButton("Delete account", viewModel::askDelete, style = ButtonStyle.Danger)
            }
        }
    }

    when (picking) {
        "theme" -> OptionSheet("Appearance", ThemeMode.entries.map { it.name to it.label }, state.themeMode.name, { viewModel.setThemeMode(ThemeMode.valueOf(it)) }) { picking = null }
        "widget" -> OptionSheet(
            "Widget transparency", WIDGET_TRANSPARENCY_LEVELS.map { it.toString() to "$it%" }, transparency.toString(),
            { viewModel.setWidgetTransparency(it.toInt()) },
        ) { picking = null }
        "currency" -> CurrencySheet(state.currency.code, viewModel::setCurrency) { picking = null }
        "payment" -> OptionSheet("Default payment", PAYMENT_METHODS.map { it to it }, defaultPayment, viewModel::setDefaultPayment) { picking = null }
        "time" -> TimeDialog(state.reminderAt, viewModel::setReminderTime) { picking = null }
    }
    DeletionDialog(deletion, viewModel)
}

/** The page in docs/, served by GitHub Pages. */
private const val PRIVACY_POLICY_URL = "https://shuvrasankha.github.io/spendwise-android/privacy-policy.html"

/** One choice per row, a tick on the current one; picking closes the sheet. */
@Composable
private fun OptionRow(title: String, selected: Boolean, sub: String? = null, lead: String? = null, onClick: () -> Unit) {
    val c = SpendWiseTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = Spacing.gutter),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (lead != null) Text(lead, style = SwType.heading, color = c.text, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SwType.body, color = c.text)
            if (sub != null) Text(sub, style = SwType.caption, color = c.textMuted)
        }
        if (selected) Icon(SwIcons.Check, "Selected", Modifier.size(20.dp), tint = c.text)
    }
}

@Composable
private fun OptionSheet(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) =
    SwSheet(title, onDismiss) {
        Column {
            options.forEach { (id, label) -> OptionRow(label, id == current) { onPick(id); onDismiss() } }
        }
    }

@Composable
private fun CurrencySheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) CURRENCIES else CURRENCIES.filter { it.code.contains(q, true) || it.name.contains(q, true) || it.symbol.contains(q, true) }
    }
    SwSheet("Currency", onDismiss) {
        Box(Modifier.padding(horizontal = Spacing.gutter)) { SwField(query, { query = it }, "Search currency", leading = SwIcons.Search) }
        if (shown.isEmpty()) Text("No currency matches.", style = SwType.body, color = SpendWiseTheme.colors.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter))
        Column {
            shown.forEach { currency -> OptionRow(currency.name, currency.code == current, sub = currency.code, lead = currency.symbol) { onPick(currency.code); onDismiss() } }
        }
    }
}

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
