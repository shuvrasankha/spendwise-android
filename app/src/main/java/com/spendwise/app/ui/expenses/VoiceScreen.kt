package com.spendwise.app.ui.expenses

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.DEBT_TYPES
import com.spendwise.app.domain.ENTRY_DEBT
import com.spendwise.app.domain.ENTRY_EXPENSE
import com.spendwise.app.domain.EXPENSE_CATEGORIES
import com.spendwise.app.domain.INCOME_PAYMENT_TYPES
import com.spendwise.app.domain.PAYMENT_METHODS
import com.spendwise.app.domain.THEY_OWE
import com.spendwise.app.domain.VOICE_LANGUAGES
import com.spendwise.app.domain.VoiceEntry
import com.spendwise.app.domain.isCard
import com.spendwise.app.domain.voiceSaveLabel
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.DateChips
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.SettingsGroup
import com.spendwise.app.ui.components.SettingsRow
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.Trailing
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.LocalDate

/** Speak, type or photograph (spec §7.4): speech input, a text box or a receipt photo, then a review card per entry. */
@Composable
fun VoiceScreen(onDone: () -> Unit, vm: VoiceViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var noMic by remember { mutableStateOf(false) }
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    // The system speech UI: no microphone permission for the app, and it speaks the picked language
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) {
            vm.setText(heard)
            vm.submit()
        }
    }

    // Receipts: the camera saves into the app's cache; the system photo picker needs no permission
    var photo by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken -> vm.scanReceipt(photo.takeIf { taken }) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.scanReceipt(it) }

    Scaffold(contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SubPageTopBar("Speak, type or scan", onDone) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Language")
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VOICE_LANGUAGES.forEach { l -> SwChip(l.label, state.language == l, { vm.setLanguage(l) }) }
                    }
                }
            }
            item {
                Box(gutter) { SwField(state.text, vm::setText, "Say what you spent, earned or lent, or paste a bank SMS", singleLine = false) }
            }
            item {
                Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwButton(
                        "Speak",
                        onClick = {
                            if (vm.offlineForMic()) return@SwButton
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, state.language.code)
                            try {
                                speech.launch(intent)
                            } catch (_: ActivityNotFoundException) {
                                noMic = true
                            }
                        },
                        modifier = Modifier.weight(1f), style = ButtonStyle.Secondary, enabled = !state.reading, leading = SwIcons.Mic,
                    )
                    SwButton(
                        "Take a photo", { vm.newPhotoUri().also { photo = it; camera.launch(it) } },
                        Modifier.weight(1f), style = ButtonStyle.Secondary, enabled = !state.reading, leading = SwIcons.Camera,
                    )
                }
            }
            item {
                Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwButton(
                        "From gallery", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        Modifier.weight(1f), style = ButtonStyle.Secondary, enabled = !state.reading, leading = SwIcons.Image,
                    )
                    SwButton("Read it", vm::submit, Modifier.weight(1f), enabled = !state.reading)
                }
            }
            if (noMic) item { Text("Speech input isn't available on this device. Type it instead.", style = SwType.label, color = c.negative, modifier = gutter) }
            if (state.reading) item { LinearProgressIndicator(gutter.fillMaxWidth(), color = c.text, trackColor = c.line) }
            state.message?.let { m -> item { Text(m.text, style = SwType.label, color = if (m.success) c.positive else c.negative, modifier = gutter) } }
            itemsIndexed(state.entries, key = { i, _ -> state.ids[i] }) { i, e -> ReviewCard(i, e, state.canShare, vm) }
            if (state.entries.isNotEmpty()) item {
                Box(gutter) { SwButton(voiceSaveLabel(state.entries) { formatAmount(it, currency) }, vm::save, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** One entry the AI or the SMS reader found, with every field its Add form has. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(i: Int, e: VoiceEntry, canShare: Boolean, vm: VoiceViewModel) {
    val c = SpendWiseTheme.colors
    val kind = when (e.type) { ENTRY_EXPENSE -> "Expense"; ENTRY_DEBT -> "Debt"; else -> "Income" }
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(gutter, color = c.line)
        Row(gutter, verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}. $kind", style = SwType.heading, color = c.text, modifier = Modifier.weight(1f))
            SwButton("Remove", { vm.remove(i) }, Modifier.semantics { contentDescription = "Remove entry ${i + 1}" }, style = ButtonStyle.Danger, compact = true)
        }
        Box(gutter) {
            SwField(e.amountText, { vm.setAmount(i, it) }, "Amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        DateChips(e.date, LocalDate.now(), { d -> vm.edit(i) { it.copy(date = d) } })
        when (e.type) {
            ENTRY_EXPENSE -> {
                TextBox("Description", e.description.takeIf { it != "-" }.orEmpty()) { t -> vm.edit(i) { it.copy(description = t.trim().ifEmpty { "-" }) } }
                Chips("Category", EXPENSE_CATEGORIES, e.category) { c2 -> vm.edit(i) { it.copy(category = c2) } }
                Chips("Paid with", PAYMENT_METHODS, e.payment) { p -> vm.edit(i) { it.copy(payment = p) } }
                if (isCard(e.payment)) TextBox("Card name (optional)", e.cardName) { t -> vm.edit(i) { it.copy(cardName = t.trim()) } }
                // Typed as text; the entry keeps the parsed tags, so a space being typed isn't eaten
                var tags by remember { mutableStateOf(e.tags.joinToString(" ")) }
                TextBox("Tags (optional)", tags) { tags = it; vm.setTags(i, it) }
                SettingsGroup(null) { SettingsRow("Repeats every month", trailing = Trailing.Switch(e.repeat) { r -> vm.edit(i) { it.copy(repeat = r) } }) }
                if (e.repeat) {
                    Box(gutter) {
                        SwField(
                            e.emiRaw, { vm.setEmi(i, it) }, "EMI: total payments (optional)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
            ENTRY_DEBT -> {
                Chips("Type", DEBT_TYPES, e.debtType, label = { if (it == THEY_OWE) "They owe me" else "I owe them" }) { t -> vm.edit(i) { it.copy(debtType = t) } }
                TextBox("Person", e.person) { t -> vm.edit(i) { it.copy(person = t.trim()) } }
                if (canShare) TextBox("Their email, to share it (optional)", e.email, KeyboardType.Email) { vm.setEmail(i, it) }
            }
            else -> {
                TextBox("Source", e.source) { t -> vm.edit(i) { it.copy(source = t.trim()) } }
                Chips("Received as", INCOME_PAYMENT_TYPES, e.paymentType) { p -> vm.edit(i) { it.copy(paymentType = p) } }
                TextBox("Bank or wallet (optional)", e.bank) { t -> vm.edit(i) { it.copy(bank = t.trim()) } }
            }
        }
        TextBox("Notes (optional)", e.notes) { t -> vm.edit(i) { it.copy(notes = t.trim()) } }
    }
}

/** A text field that shows what's typed; [onText] gets it as typed (the entry trims). */
@Composable
private fun TextBox(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onText: (String) -> Unit) {
    var text by remember(label) { mutableStateOf(value) }
    Box(Modifier.padding(horizontal = Spacing.gutter)) {
        SwField(text, { text = it; onText(it) }, label, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(title: String, options: List<String>, selected: String, label: (String) -> String = { it }, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(title)
        FlowRow(Modifier.padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> SwChip(label(o), selected == o, { onPick(o) }) }
        }
    }
}
