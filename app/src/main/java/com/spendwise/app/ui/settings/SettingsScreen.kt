package com.spendwise.app.ui.settings

import com.spendwise.app.data.AppIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateContentSize
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
import com.spendwise.app.ui.components.IconAction
import com.spendwise.app.domain.PASSWORD_RULES
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.spendwise.app.ui.lock.PinCheckPad
import com.spendwise.app.ui.lock.SetPinPad
import com.spendwise.app.ui.lock.canUseBiometric
import com.spendwise.app.ui.lock.confirmBiometric
import com.spendwise.app.ui.lock.findActivity
import com.spendwise.app.ui.lock.confirmIdentity
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** Settings (spec §1.9): Display, Notifications, Security and About, then Log out; plus Delete account (§8.2). */
@Composable
fun SettingsScreen(onBack: () -> Unit, onAiProvider: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    val passwordChange by viewModel.password.collectAsStateWithLifecycle()
    val defaultPayment by viewModel.defaultPayment.collectAsStateWithLifecycle()
    val transparency by viewModel.widgetTransparency.collectAsStateWithLifecycle()
    val appIcon by viewModel.appIcon.collectAsStateWithLifecycle()
    val hapticOn by viewModel.hapticOn.collectAsStateWithLifecycle()
    val aiLabel by viewModel.aiLabel.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var pinStep by rememberSaveable { mutableStateOf<PinStep?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.turnReminderOn(it) }
    val weeklyOn by viewModel.weeklyNote.collectAsStateWithLifecycle()
    val askWeeklyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.setWeeklyNote(true, it) }
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

            if (viewModel.canChangePassword) {
                SettingsGroup("Account") {
                    SettingsRow("Change password", icon = SwIcons.Lock, onClick = viewModel::openPasswordChange)
                }
            }

            SettingsGroup("Display") {
                SettingsRow("Appearance", icon = state.themeMode.icon, trailing = Trailing.Value(state.themeMode.label), onClick = { picking = "theme" })
                SettingsRow("App icon", icon = SwIcons.Image, trailing = Trailing.Value(appIcon.label), divider = true, onClick = { picking = "icon" })
                SettingsRow("Widget transparency", icon = SwIcons.Blend, trailing = Trailing.Value("$transparency%"), divider = true, onClick = { picking = "widget" })
                SettingsRow("Currency", icon = SwIcons.Banknote, trailing = Trailing.Value("${state.currency.symbol} ${state.currency.code}"), divider = true, onClick = { picking = "currency" })
                SettingsRow("Default payment", icon = SwIcons.CreditCard, trailing = Trailing.Value(defaultPayment), divider = true, onClick = { picking = "payment" })
                SettingsRow("Haptic feedback", icon = SwIcons.Vibrate, trailing = Trailing.Switch(hapticOn) { viewModel.setHaptic(it) }, divider = true)
            }

            SettingsGroup("AI") {
                SettingsRow("AI provider", icon = SwIcons.Sparkles, trailing = Trailing.Value(aiLabel), onClick = onAiProvider)
            }

            // The note under the group appears and goes, so let the block grow instead of jumping
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsGroup("Notifications") {
                    SettingsRow("Daily reminder", icon = SwIcons.Bell, trailing = Trailing.Switch(state.reminderOn) { on ->
                        when {
                            !on -> viewModel.turnReminderOff()
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> viewModel.turnReminderOn(true)
                        }
                    })
                    SettingsRow("Reminder time", icon = SwIcons.Clock, trailing = Trailing.Value(state.reminderAt), divider = true, onClick = { picking = "time" })
                    SettingsRow("Weekly money note", icon = SwIcons.Calendar, trailing = Trailing.Switch(weeklyOn) { on ->
                        when {
                            !on -> viewModel.setWeeklyNote(false)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> askWeeklyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> viewModel.setWeeklyNote(true)
                        }
                    }, divider = true)
                }
                state.reminderNote?.let { Text(it, style = SwType.label, color = c.textMuted, modifier = gutter) }
            }

            val activity = LocalContext.current.findActivity()
            val hasPin by viewModel.lockHasPin.collectAsStateWithLifecycle()
            val bioOn by viewModel.lockBiometric.collectAsStateWithLifecycle()
            val bioAvailable = remember { canUseBiometric(activity) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsGroup("Security") {
                    SettingsRow("App lock", icon = SwIcons.Lock, trailing = Trailing.Switch(state.lockOn) { on ->
                        when {
                            on -> pinStep = PinStep.Set
                            hasPin -> pinStep = PinStep.Off
                            else -> activity.confirmIdentity { ok -> if (ok) viewModel.lockOff() } // a lock from before PINs
                        }
                    })
                    if (state.lockOn && hasPin) {
                        SettingsRow("Change PIN", icon = SwIcons.Lock, divider = true, onClick = { pinStep = PinStep.Change })
                        if (bioAvailable) SettingsRow("Unlock with fingerprint or face", icon = SwIcons.Shield, divider = true, trailing = Trailing.Switch(bioOn) { on ->
                            if (on) activity.confirmBiometric { ok -> if (ok) viewModel.setLockBiometric(true) } else viewModel.setLockBiometric(false)
                        })
                    }
                }
                Text(
                    "Asks for your PIN when SpendWise opens, and after 5 minutes away. Forgot it? Sign out from the lock screen and sign in again. This device only.",
                    style = SwType.label, color = c.textMuted, modifier = gutter,
                )
            }
            pinStep?.let { step ->
                val close = { pinStep = null }
                Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
                    when (step) {
                        PinStep.Set -> SetPinPad("Choose a PIN", { viewModel.setPin(it); close() }, onCancel = close)
                        PinStep.NewPin -> SetPinPad("Choose a new PIN", { viewModel.setPin(it); close() }, onCancel = close)
                        PinStep.Off -> PinCheckPad("Enter your PIN to turn off app lock", viewModel::tryPin, viewModel::pinWaitLeft, onOk = { viewModel.lockOff(); close() }, onCancel = close)
                        PinStep.Change -> PinCheckPad("Enter your current PIN", viewModel::tryPin, viewModel::pinWaitLeft, onOk = { pinStep = PinStep.NewPin }, onCancel = close)
                    }
                }
            }

            // The note under the group appears and goes, so let the block grow instead of jumping
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsGroup("About") {
                    SettingsRow("App tour", icon = SwIcons.Smartphone, onClick = viewModel::replayTour)
                    SettingsRow("Privacy policy", icon = SwIcons.Shield, divider = true, onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) })
                    SettingsRow("Send feedback", icon = SwIcons.Mail, divider = true, onClick = { context.sendFeedback(viewModel.installedVersion) })
                    SettingsRow("Rate on Play Store", icon = SwIcons.Star, divider = true, onClick = {
                        // No Play Store app (a GitHub install on a de-Googled phone): the web listing instead
                        try { uriHandler.openUri("market://details?id=${context.packageName}") } catch (_: Exception) { uriHandler.openUri(PLAY_URL + context.packageName) }
                    })
                    SettingsRow("Open-source licences", icon = SwIcons.FileText, divider = true, onClick = { picking = "licences" })
                    SettingsRow(
                        "Check for updates", icon = SwIcons.Download, divider = true, onClick = viewModel::checkForUpdates,
                        trailing = Trailing.Value(if (update == UpdateCheck.Checking) "Checking…" else viewModel.installedVersion),
                    )
                }
                updateNote(update)?.let { Text(it, style = SwType.label, color = c.textMuted, modifier = gutter) }
            }

            Column(gutter, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SwButton("Log out", viewModel::logOut, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, leading = SwIcons.LogOut)
                SwButton("Delete account", viewModel::askDelete, style = ButtonStyle.Danger)
            }
        }
    }

    (update as? UpdateCheck.Available)?.let { UpdateDialog(it.release.version, viewModel::installUpdate, viewModel::dismissUpdate) }

    when (picking) {
        "theme" -> OptionSheet("Appearance", ThemeMode.entries.map { it.name to it.label }, state.themeMode.name, { viewModel.setThemeMode(ThemeMode.valueOf(it)) }, { ThemeMode.valueOf(it).icon }) { picking = null }
        "widget" -> OptionSheet(
            "Widget transparency", WIDGET_TRANSPARENCY_LEVELS.map { it.toString() to "$it%" }, transparency.toString(),
            { viewModel.setWidgetTransparency(it.toInt()) },
        ) { picking = null }
        "icon" -> AppIconSheet(appIcon, viewModel::setAppIcon) { picking = null }
        "currency" -> CurrencySheet(state.currency.code, viewModel::setCurrency) { picking = null }
        "payment" -> OptionSheet("Default payment", PAYMENT_METHODS.map { it to it }, defaultPayment, viewModel::setDefaultPayment) { picking = null }
        "licences" -> LicencesSheet(uriHandler::openUri) { picking = null }
        "time" -> TimeDialog(state.reminderAt, viewModel::setReminderTime) { picking = null }
    }
    DeletionDialog(deletion, viewModel)
    passwordChange?.let { ChangePasswordSheet(it, viewModel) }
}

@Composable
private fun ChangePasswordSheet(form: PasswordChange, vm: SettingsViewModel) {
    val c = SpendWiseTheme.colors
    var show by rememberSaveable { mutableStateOf(false) }
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    val hide = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val toggle: @Composable () -> Unit = {
        IconAction(if (show) SwIcons.EyeOff else SwIcons.Eye, if (show) "Hide passwords" else "Show passwords", { show = !show })
    }
    SwSheet(
        if (form.done) "Password changed" else "Change password", vm::closePasswordChange,
        footer = {
            form.error?.let { Text(it, style = SwType.label, color = c.negative, modifier = gutter.semantics { liveRegion = LiveRegionMode.Polite }) }
            Box(gutter) {
                if (form.done) SwButton("Done", vm::closePasswordChange, Modifier.fillMaxWidth())
                else SwButton(if (form.busy) "Saving…" else "Change password", vm::changePassword, Modifier.fillMaxWidth(), enabled = !form.busy)
            }
        },
    ) {
        if (form.done) {
            Text("Your password was changed. Use the new one the next time you log in.", style = SwType.body, color = c.textMuted, modifier = gutter)
        } else {
            Box(gutter) {
                SwField(
                    form.current, vm::setCurrentPassword, "Current password", leading = SwIcons.Lock, visualTransformation = hide, trailing = toggle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                )
            }
            Box(gutter) {
                SwField(
                    form.new, vm::setNewPassword, "New password", leading = SwIcons.Lock, visualTransformation = hide,
                    supportingText = PASSWORD_RULES,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                )
            }
            Box(gutter) {
                SwField(
                    form.confirm, vm::setConfirmPassword, "Confirm new password", leading = SwIcons.Lock, visualTransformation = hide,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.changePassword() }),
                )
            }
        }
    }
}

/** The page in docs/, served by GitHub Pages. */
private const val PLAY_URL = "https://play.google.com/store/apps/details?id="

// The contact address from the privacy policy (docs/privacy-policy.html)
/** The PIN pad Settings is showing, if any. */
private enum class PinStep { Set, Off, Change, NewPin }

private const val FEEDBACK_EMAIL = "contact.spend.wise.app@gmail.com"

/** Opens the email app addressed to us, with the version and device filled in so reports say what they ran on. */
private fun Context.sendFeedback(version: String) {
    val body = "\n\n—\nSpendWise $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, "SpendWise feedback ($version)")
        .putExtra(Intent.EXTRA_TEXT, body)
    try { startActivity(mail) } catch (_: ActivityNotFoundException) { Toast.makeText(this, "No email app found. Write to $FEEDBACK_EMAIL", Toast.LENGTH_LONG).show() }
}

/** What the app is built on, by whom, under which licence; tapping one opens the licence text. */
private val LICENCES = listOf(
    Triple("AndroidX and Jetpack Compose", "Google · Apache 2.0", APACHE),
    Triple("Material 3 for Compose", "Google · Apache 2.0", APACHE),
    Triple("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains · Apache 2.0", APACHE),
    Triple("Firebase Android SDK", "Google · Apache 2.0", APACHE),
    Triple("Dagger Hilt", "Google · Apache 2.0", APACHE),
    Triple("Play In-App Updates, Sign in with Google", "Google · Google APIs Terms of Service", "https://developers.google.com/terms"),
    Triple("Inter typeface", "Rasmus Andersson · SIL Open Font License 1.1", "https://openfontlicense.org/open-font-license-official-text/"),
    Triple("Lucide icons", "Lucide contributors · ISC License", "https://lucide.dev/license"),
)
private const val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"

@Composable
private fun LicencesSheet(open: (String) -> Unit, onDismiss: () -> Unit) = SwSheet("Open-source licences", onDismiss) {
    val c = SpendWiseTheme.colors
    LICENCES.forEach { (name, by, url) ->
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open licence") { open(url) }.padding(horizontal = Spacing.gutter, vertical = 10.dp)) {
            Text(name, style = SwType.body, color = c.text)
            Text(by, style = SwType.label, color = c.textMuted)
        }
    }
}

private const val PRIVACY_POLICY_URL = "https://shuvrasankha.github.io/spendwise-android/privacy-policy.html"

/** One choice per row, a tick on the current one; picking closes the sheet. */
@Composable
internal fun OptionRow(
    title: String, selected: Boolean, sub: String? = null, lead: String? = null, icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    val c = SpendWiseTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = Spacing.gutter),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = c.text)
        leading?.invoke()
        if (lead != null) Text(lead, style = SwType.heading, color = c.text, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SwType.body, color = c.text)
            if (sub != null) Text(sub, style = SwType.caption, color = c.textMuted)
        }
        if (selected) Icon(SwIcons.Check, "Selected", Modifier.size(20.dp), tint = c.text)
    }
}

@Composable
private fun OptionSheet(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, icon: (String) -> ImageVector? = { null }, onDismiss: () -> Unit) =
    SwSheet(title, onDismiss) {
        Column {
            options.forEach { (id, label) -> OptionRow(label, id == current, icon = icon(id)) { onPick(id); onDismiss() } }
        }
    }

/** The launcher icons, each previewed as a launcher draws it: the 108dp layer masked to its inner 72dp. */
@Composable
private fun AppIconSheet(current: AppIcon, onPick: (AppIcon) -> Unit, onDismiss: () -> Unit) =
    SwSheet("App icon", onDismiss) {
        Column {
            AppIcon.entries.forEach { icon ->
                OptionRow(
                    icon.label, icon == current, sub = if (icon == AppIcon.DARK) "Default" else null,
                    leading = {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(icon.tile))
                                .border(1.dp, SpendWiseTheme.colors.line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Image(painterResource(icon.mark), null, Modifier.requiredSize(60.dp)) }
                    },
                ) { onPick(icon); onDismiss() }
            }
            Text(
                "The new icon shows once you leave SpendWise. Your home screen may drop the old one; add it back from the app list.",
                style = SwType.label, color = SpendWiseTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = 8.dp),
            )
        }
    }

@Composable
private fun CurrencySheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) CURRENCIES else CURRENCIES.filter { it.code.contains(q, true) || it.name.contains(q, true) || it.symbol.contains(q, true) }
    }
    SwSheet("Currency", onDismiss, scroll = false) {
        Box(Modifier.padding(horizontal = Spacing.gutter)) { SwField(query, { query = it }, "Search currency", leading = SwIcons.Search) }
        if (shown.isEmpty()) Text("No currency matches.", style = SwType.body, color = SpendWiseTheme.colors.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(shown, key = { it.code }) { currency -> OptionRow(currency.name, currency.code == current, sub = currency.code, lead = currency.symbol) { onPick(currency.code); onDismiss() } }
        }
    }
}

/** A newer GitHub release: download and install it now, or later. */
@Composable
private fun UpdateDialog(version: String, onUpdate: () -> Unit, onLater: () -> Unit) = AlertDialog(
    onDismissRequest = onLater,
    title = { Text("Update available") },
    text = { Text("Version ${version.removePrefix("v")} is ready to download. Your data stays as it is.") },
    confirmButton = { TextButton(onClick = onUpdate) { Text("Update") } },
    dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
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

private val ThemeMode.icon
    get() = when (this) {
        ThemeMode.SYSTEM -> SwIcons.Monitor
        ThemeMode.LIGHT -> SwIcons.Sun
        ThemeMode.DARK -> SwIcons.Moon
    }

private val ThemeMode.label
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
