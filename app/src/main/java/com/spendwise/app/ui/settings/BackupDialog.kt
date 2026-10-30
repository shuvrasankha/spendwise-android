package com.spendwise.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwSheet
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** Backup & restore, worded as the web's dialog (settings.html › #backup-modal). */
@Composable
fun BackupDialog(vm: BackupViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), vm::export)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::pick)
    if (!state.open) return
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)

    SwSheet("Backup & restore", vm::close) {
        Text("One file with all your expenses, income and debts.", style = SwType.label, color = c.textMuted, modifier = gutter)
        Section("Download a backup", "Saves everything as a .json file. Keep it somewhere safe.") {
            SwButton("Download", { save.launch(vm.fileName) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = !state.busy, leading = SwIcons.Download)
        }
        HorizontalDivider(gutter, color = c.line)
        Section("Restore from a backup", "Brings back what's in a SpendWise backup. Entries you already have aren't doubled.") {
            SwButton(
                state.pickedName ?: "Choose a file", { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = !state.busy, leading = SwIcons.Upload,
            )
            SwButton("Restore", vm::restore, Modifier.fillMaxWidth(), enabled = state.picked != null && !state.busy)
        }
        state.status?.let {
            Text(it.text, style = SwType.label, color = if (it.error) c.negative else c.text, modifier = gutter.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun Section(title: String, note: String, buttons: @Composable () -> Unit) =
    Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = SwType.heading, color = SpendWiseTheme.colors.text)
        Text(note, style = SwType.label, color = SpendWiseTheme.colors.textMuted)
        buttons()
    }
