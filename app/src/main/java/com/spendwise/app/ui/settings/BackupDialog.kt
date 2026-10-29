package com.spendwise.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.spendwise.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Backup & restore, worded as the web's dialog (settings.html › #backup-modal). */
@Composable
fun BackupDialog(vm: BackupViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), vm::export)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::pick)
    if (!state.open) return

    AlertDialog(
        onDismissRequest = vm::close,
        title = { Text("Backup & restore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("One file with all your expenses, income and debts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                BackupSection("Download a backup", "Saves everything as a .json file. Keep it somewhere safe.") {
                    OutlinedButton(onClick = { save.launch(vm.fileName) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        ButtonLabel(R.drawable.ic_download, "Download")
                    }
                }
                HorizontalDivider()
                BackupSection("Restore from a backup", "Brings back what's in a SpendWise backup. Entries you already have aren't doubled.") {
                    OutlinedButton(
                        onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                    ) { ButtonLabel(R.drawable.ic_upload, state.pickedName ?: "Choose a file") }
                    Button(onClick = vm::restore, enabled = state.picked != null && !state.busy, modifier = Modifier.fillMaxWidth()) {
                        ButtonLabel(R.drawable.ic_restore, "Restore")
                    }
                }
                state.status?.let {
                    Text(
                        it.text,
                        color = if (it.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::close, enabled = !state.busy) { Text("Close") } },
    )
}

@Composable
private fun BackupSection(title: String, note: String, buttons: @Composable ColumnScope.() -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant)
    buttons()
}

/** A button's icon and text; the icon is decorative, the text names the button. */
@Composable
internal fun RowScope.ButtonLabel(icon: Int, text: String) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text)
}
