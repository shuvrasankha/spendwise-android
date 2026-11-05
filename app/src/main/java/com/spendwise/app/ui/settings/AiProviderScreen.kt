package com.spendwise.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.data.ai.NVIDIA_DEFAULT_MODEL
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.SettingsGroup
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** Settings › AI provider (BYOK plan §6): SpendWise AI, or the user's own NVIDIA key. */
@Composable
fun AiProviderScreen(onBack: () -> Unit, vm: AiProviderViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)

    Scaffold(contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SubPageTopBar("AI provider", onBack)
            if (s.loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.text) }
                return@Column
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsGroup("Use") {
                    OptionRow("SpendWise AI", !s.byok, sub = "Built in, with daily limits", onClick = vm::useSpendWise)
                    HorizontalDivider(color = c.line)
                    OptionRow("My own API key", s.byok, sub = "NVIDIA", onClick = vm::useMyKey)
                }
                s.note?.let { Text(it, style = SwType.label, color = c.text, modifier = gutter.semantics { liveRegion = LiveRegionMode.Polite }) }
            }

            KeySection(s, vm)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldLabel("Models")
                Box(gutter) {
                    SwField(s.textModel, vm::setTextModel, NVIDIA_DEFAULT_MODEL, supportingText = "Text model, for Ask, Coach, Voice, CSV and budgets. Leave empty for the default.")
                }
                Box(gutter) {
                    SwField(s.visionModel, vm::setVisionModel, NVIDIA_DEFAULT_MODEL, supportingText = "Vision model, for receipt scans. It must read images.")
                }
            }

            Column(gutter, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val running = s.test == KeyTest.Running
                SwButton(
                    if (running) "Testing…" else "Test connection", { vm.test() }, Modifier.fillMaxWidth(),
                    style = ButtonStyle.Secondary, enabled = s.keyMask != null && !running,
                )
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val t = s.test) {
                        KeyTest.Running -> CircularProgressIndicator(Modifier.size(16.dp), color = c.text, strokeWidth = 2.dp)
                        KeyTest.Passed -> Text("Connection works. You can now choose My own API key.", style = SwType.label, color = c.positive)
                        is KeyTest.Failed -> Text("Test failed: ${t.message}", style = SwType.label, color = c.negative)
                        KeyTest.Idle -> Unit
                    }
                }
            }

            Column(gutter, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "With your own key, AI requests and the figures they need go straight from this phone to NVIDIA, under NVIDIA's terms.",
                    style = SwType.label, color = c.textMuted,
                )
                Text("Your provider sets the limits and pricing. SpendWise can't see your usage or balance.", style = SwType.label, color = c.textMuted)
            }
        }
    }

    if (s.consentAsked) {
        AlertDialog(
            onDismissRequest = { vm.answerConsent(false) },
            title = { Text("Send AI requests to NVIDIA?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ask, Coach, voice entry, receipt scans, CSV import and budget suggestions will send their request to NVIDIA with your key: what you type or say, receipt photos, CSV rows, and the spending and income figures the feature needs.")
                    Text("NVIDIA handles this under its own terms and privacy policy, not SpendWise's. SpendWise doesn't see these requests.")
                    Text("You can switch back to SpendWise AI at any time.")
                }
            },
            confirmButton = { TextButton(onClick = { vm.answerConsent(true) }) { Text("I agree") } },
            dismissButton = { TextButton(onClick = { vm.answerConsent(false) }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeySection(s: AiProviderUi, vm: AiProviderViewModel) {
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    var replacing by remember { mutableStateOf(false) }
    // Not rememberSaveable: the key never goes into saved instance state
    var typed by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel("NVIDIA API key")
        if (s.keyMask != null && !replacing) {
            Row(gutter, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Key saved ••••${s.keyMask}", style = SwType.body, color = c.text, modifier = Modifier.weight(1f))
                SwButton("Replace", { replacing = true }, compact = true, style = ButtonStyle.Secondary)
                SwButton("Remove", { vm.removeKey() }, compact = true, style = ButtonStyle.Danger)
            }
        } else {
            Box(gutter) {
                SwField(
                    typed, { typed = it }, "nvapi-…", leading = SwIcons.Lock, visualTransformation = PasswordVisualTransformation(),
                    supportingText = "Get a key at build.nvidia.com. It's stored encrypted on this phone only.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                )
            }
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwButton("Save key", { vm.saveKey(typed.trim().toCharArray()); typed = ""; replacing = false }, Modifier.weight(1f), enabled = typed.isNotBlank())
                if (replacing) SwButton("Cancel", { typed = ""; replacing = false }, style = ButtonStyle.Text)
            }
        }
    }
}
