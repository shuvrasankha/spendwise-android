package com.spendwise.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.data.prefs.ThemeMode
import com.spendwise.app.domain.CURRENCIES

// Slice 1: only the per-device rows (appearance, currency). The rest of spec §1.9 comes in slice 13.
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingCurrency by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))

        Text(
            "Appearance",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
            supportingContent = { Text("${state.currency.symbol} ${state.currency.code} — ${state.currency.name}") },
            modifier = Modifier.padding(top = 16.dp).clickable { pickingCurrency = true },
        )

        Text(
            "Account",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        ListItem(
            headlineContent = { Text(state.name.ifEmpty { state.email }) },
            supportingContent = if (state.name.isEmpty()) null else ({ Text(state.email) }),
        )
        OutlinedButton(
            onClick = viewModel::logOut,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { Text("Log out") }
    }

    if (pickingCurrency) {
        AlertDialog(
            onDismissRequest = { pickingCurrency = false },
            title = { Text("Currency") },
            text = {
                LazyColumn {
                    items(CURRENCIES, key = { it.code }) { currency ->
                        val selected = currency.code == state.currency.code
                        ListItem(
                            leadingContent = { RadioButton(selected = selected, onClick = null) },
                            headlineContent = { Text("${currency.symbol} ${currency.code} — ${currency.name}") },
                            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                                viewModel.setCurrency(currency.code)
                                pickingCurrency = false
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingCurrency = false }) { Text("Close") } },
        )
    }
}

private val ThemeMode.label
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
