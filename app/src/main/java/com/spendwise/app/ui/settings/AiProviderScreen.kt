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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.R
import com.spendwise.app.data.ai.BYOK_PROVIDER_LIST
import com.spendwise.app.data.ai.ByokProvider
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

/** Settings › AI provider (BYOK plan §6): SpendWise AI, or the user's own key for one provider. */
@Composable
fun AiProviderScreen(onBack: () -> Unit, vm: AiProviderViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    var setup by rememberSaveable { mutableStateOf(false) }

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
                    OptionRow("SpendWise AI", !s.byok, sub = if (!s.byok) "In use · built in, with daily limits" else "Built in, with daily limits") { setup = false; vm.useSpendWise() }
                    HorizontalDivider(color = c.line)
                    val sub = when {
                        s.test == KeyTest.Running -> "Testing your ${s.provider.name} key…"
                        s.byok -> "In use · ${s.active.name}"
                        else -> BYOK_PROVIDER_LIST.joinToString { it.name }
                    }
                    OptionRow("My own API key", s.byok, sub = sub) { setup = true; vm.useMyKey() }
                }
                s.note?.let { Text(it, style = SwType.label, color = c.text, modifier = gutter.semantics { liveRegion = LiveRegionMode.Polite }) }
            }

            // The provider setup belongs to "My own API key": shown with SpendWise AI, its checks read as if Gemini were on
            if (s.byok || setup) {
                SettingsGroup("Provider") {
                    BYOK_PROVIDER_LIST.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = c.line)
                        OptionRow(p.name, p == s.provider, sub = if (s.byok && p == s.active) "In use" else null, leading = { ProviderIcon(p) }) { vm.pickProvider(p) }
                    }
                }

                KeySection(s, vm)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel("Models")
                    Box(gutter) {
                        SwField(s.textModel, vm::setTextModel, s.provider.defaultModel, supportingText = "Text model, for Ask, Coach, Voice, CSV and budgets. Leave empty for the default.")
                    }
                    Box(gutter) {
                        SwField(s.visionModel, vm::setVisionModel, s.provider.defaultModel, supportingText = "Vision model, for receipt scans. It must read images.")
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
                            KeyTest.Passed -> Text(if (s.byok && s.active == s.provider) "Connection works." else "Connection works. Choose My own API key to use it.", style = SwType.label, color = c.positive)
                            is KeyTest.Failed -> Text("Test failed: ${t.message}", style = SwType.label, color = c.negative)
                            KeyTest.Idle -> Unit
                        }
                    }
                }

                Column(gutter, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "With your own key, AI requests and the figures they need go straight from this phone to ${s.provider.name}, under ${s.provider.name}'s terms.",
                        style = SwType.label, color = c.textMuted,
                    )
                    Text("Your provider sets the limits and pricing. SpendWise can't see your usage or balance.", style = SwType.label, color = c.textMuted)
                }
            }
        }
    }

    if (s.consentAsked) {
        AlertDialog(
            onDismissRequest = { vm.answerConsent(false) },
            title = { Text("Send AI requests to ${s.provider.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ask, Coach, voice entry, receipt scans, CSV import and budget suggestions will send their request to ${s.provider.name} with your key: what you type or say, receipt photos, CSV rows, and the spending and income figures the feature needs.")
                    Text("${s.provider.name} handles this under its own terms and privacy policy, not SpendWise's. SpendWise doesn't see these requests.")
                    Text("You can switch back to SpendWise AI at any time.")
                }
            },
            confirmButton = { TextButton(onClick = { vm.answerConsent(true) }) { Text("I agree") } },
            dismissButton = { TextButton(onClick = { vm.answerConsent(false) }) { Text("Cancel") } },
        )
    }
}

/** The provider's own mark, in the text colour like every other icon. */
@Composable
private fun ProviderIcon(p: ByokProvider) {
    val res = when (p.id) {
        "nvidia" -> R.drawable.ic_ai_nvidia
        "openai" -> R.drawable.ic_ai_openai
        "gemini" -> R.drawable.ic_ai_gemini
        "groq" -> R.drawable.ic_ai_groq
        else -> R.drawable.ic_ai_anthropic
    }
    Icon(painterResource(res), null, Modifier.size(22.dp), tint = SpendWiseTheme.colors.text)
}

@Composable
private fun KeySection(s: AiProviderUi, vm: AiProviderViewModel) {
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    // Keyed by provider so a half-typed key never carries over. Not rememberSaveable: the key stays out of saved state
    var replacing by remember(s.provider) { mutableStateOf(false) }
    var typed by remember(s.provider) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel("${s.provider.name} API key")
        if (s.keyMask != null && !replacing) {
            Row(gutter, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Key saved ••••${s.keyMask}", style = SwType.body, color = c.text, modifier = Modifier.weight(1f))
                SwButton("Replace", { replacing = true }, compact = true, style = ButtonStyle.Secondary)
                SwButton("Remove", { vm.removeKey() }, compact = true, style = ButtonStyle.Danger)
            }
        } else {
            Box(gutter) {
                SwField(
                    typed, { typed = it }, s.provider.keyHint, leading = SwIcons.Lock, visualTransformation = PasswordVisualTransformation(),
                    supportingText = "${s.provider.keyHelp} It's stored encrypted on this phone only.",
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
