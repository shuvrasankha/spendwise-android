package com.spendwise.app.ui.expenses

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.R
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
import com.spendwise.app.ui.format.formatMoney

/** Speak or type (spec §7.4): the system's speech input or a text box, then a review card per entry. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceScreen(onDone: () -> Unit, vm: VoiceViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var noMic by remember { mutableStateOf(false) }
    // The system speech UI: no microphone permission for the app, and it speaks the picked language
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) {
            vm.setText(heard)
            vm.submit()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Speak or type") },
                windowInsets = WindowInsets(0),
                navigationIcon = { IconButton(onClick = onDone) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Language", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VOICE_LANGUAGES.forEach { l ->
                            FilterChip(
                                selected = state.language == l,
                                onClick = { vm.setLanguage(l) },
                                label = { Text(l.label) },
                                modifier = Modifier.semantics { contentDescription = l.name },
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = vm::setText,
                    label = { Text("Say what you spent, earned or lent, or paste a bank SMS") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        enabled = !state.reading,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (vm.offlineForMic()) return@OutlinedButton
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, state.language.code)
                            try {
                                speech.launch(intent)
                            } catch (_: ActivityNotFoundException) {
                                noMic = true
                            }
                        },
                    ) { Text("Speak") }
                    Button(onClick = vm::submit, enabled = !state.reading, modifier = Modifier.weight(1f)) { Text("Read it") }
                }
            }
            if (noMic) item { Text("Speech input isn't available on this device. Type it instead.", color = MaterialTheme.colorScheme.error) }
            if (state.reading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { FormMessageText(state.message) }
            itemsIndexed(state.entries, key = { i, _ -> state.ids[i] }) { i, e ->
                ReviewCard(i, e, state.canShare, vm)
            }
            if (state.entries.isNotEmpty()) item {
                Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) {
                    Text(voiceSaveLabel(state.entries) { formatMoney(it, currency) })
                }
            }
        }
    }
}

/** One entry the AI or the SMS reader found, with every field its Add form has. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(i: Int, e: VoiceEntry, canShare: Boolean, vm: VoiceViewModel) {
    val kind = when (e.type) { ENTRY_EXPENSE -> "Expense"; ENTRY_DEBT -> "Debt"; else -> "Income" }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}. $kind", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.remove(i) }, modifier = Modifier.semantics { contentDescription = "Remove entry ${i + 1}" }) { Text("Remove") }
        }
        OutlinedTextField(
            value = e.amountText,
            onValueChange = { vm.setAmount(i, it) },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(e.date) { d -> vm.edit(i) { it.copy(date = d) } }
        when (e.type) {
            ENTRY_EXPENSE -> {
                TextBox("Description", e.description.takeIf { it != "-" }.orEmpty()) { t -> vm.edit(i) { it.copy(description = t.trim().ifEmpty { "-" }) } }
                Chips("Category", EXPENSE_CATEGORIES, e.category) { c -> vm.edit(i) { it.copy(category = c) } }
                Chips("Payment method", PAYMENT_METHODS, e.payment) { p -> vm.edit(i) { it.copy(payment = p) } }
                if (isCard(e.payment)) TextBox("Card name (optional)", e.cardName) { t -> vm.edit(i) { it.copy(cardName = t.trim()) } }
                // Typed as text; the entry keeps the parsed tags, so a space being typed isn't eaten
                var tags by remember { mutableStateOf(e.tags.joinToString(" ")) }
                TextBox("Tags (optional)", tags) { tags = it; vm.setTags(i, it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e.repeat, onCheckedChange = { r -> vm.edit(i) { it.copy(repeat = r) } })
                    Text("Repeats every month")
                }
                if (e.repeat) {
                    OutlinedTextField(
                        value = e.emiRaw,
                        onValueChange = { vm.setEmi(i, it) },
                        label = { Text("EMI: total payments (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onText(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(title: String, options: List<String>, selected: String, label: (String) -> String = { it }, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(selected = selected == o, onClick = { onPick(o) }, label = { Text(label(o)) }) }
        }
    }
}
