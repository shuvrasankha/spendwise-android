package com.spendwise.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("One file with all your expenses, income and debts.")
                Text("Download a backup", style = MaterialTheme.typography.titleSmall)
                Text("Saves everything as a .json file. Keep it somewhere safe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { save.launch(vm.fileName) }, enabled = !state.busy) { Text("Download") }
                Text("Restore from a backup", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Brings back what's in a SpendWise backup. Entries you already have aren't doubled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !state.busy) {
                    Text(state.pickedName ?: "Choose a file")
                }
                OutlinedButton(onClick = vm::restore, enabled = state.picked != null && !state.busy) { Text("Restore") }
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
