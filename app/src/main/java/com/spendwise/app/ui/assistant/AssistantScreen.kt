package com.spendwise.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.ASK_MAX
import com.spendwise.app.domain.ASSISTANT_ADVICE_PROMPTS
import com.spendwise.app.domain.ASSISTANT_AI_NOTE
import com.spendwise.app.domain.ASSISTANT_NUMBER_PROMPTS
import com.spendwise.app.domain.AskHistory
import com.spendwise.app.domain.BOLD_OFF
import com.spendwise.app.domain.BOLD_ON
import com.spendwise.app.domain.FinCard
import com.spendwise.app.domain.ITALIC_OFF
import com.spendwise.app.domain.ITALIC_ON
import com.spendwise.app.domain.MdBlock
import com.spendwise.app.domain.markdownBlocks
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** One conversation for questions about the numbers and advice on them. */
@Composable
fun AssistantScreen(onBack: () -> Unit, onOpenHistory: (AskHistory) -> Unit, vm: AssistantViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val consentAsked by vm.consentAsked.collectAsStateWithLifecycle()
    if (consentAsked) ConsentDialog(vm::answerConsent)
    val c = SpendWiseTheme.colors
    val list = rememberLazyListState()
    // The list's last item is the newest message, or the typing and error line under it
    LaunchedEffect(state.rows.size, state.busy, state.error) { if (state.rows.isNotEmpty()) list.animateScrollToItem(Int.MAX_VALUE.coerceAtMost(state.rows.size + 1)) }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        SubPageTopBar("Ask SpendWise", onBack, actions = {
            if (state.rows.isNotEmpty()) SwButton("New chat", vm::newChat, style = ButtonStyle.Text, compact = true, enabled = !state.busy)
        })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, state = list,
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loadFailed) item { Text("Couldn't load your data. Check your connection and try again.", style = SwType.body, color = c.negative) }
            else if (state.rows.isEmpty() && !state.busy) item { Starters(vm::send) }
            // A chat only grows at the end (New chat clears it), so a row's place is its identity
            itemsIndexed(state.rows, key = { i, _ -> "row-$i" }, contentType = { _, r -> r::class }) { _, r ->
                when (r) {
                    is AssistantRow.Question -> UserMessage(r.text)
                    is AssistantRow.Calculated -> CardView(r.card, onOpenHistory)
                    is AssistantRow.Advice -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        r.card?.let { CardView(it, onOpenHistory) }
                        r.text?.let {
                            Text(ASSISTANT_AI_NOTE, style = SwType.caption, color = c.textMuted)
                            Answer(it)
                        }
                    }
                    is AssistantRow.Clarify -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(r.text, style = SwType.body, color = c.text)
                        ChipRow(r.options.map { it.label }) { label -> vm.send(r.options.first { it.label == label }.question) }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.busy) Text("Working on it…", style = SwType.label, color = c.textMuted)
                    state.error?.let { e ->
                        Text(e, style = SwType.body, color = c.negative)
                        if (state.canRetry) SwButton("Try again", vm::retry, style = ButtonStyle.Text, compact = true)
                    }
                }
            }
        }
        var text by rememberSaveable { mutableStateOf("") }
        val send = { if (!state.busy && text.isNotBlank()) { vm.send(text); text = "" } }
        Box(Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = 12.dp)) {
            SwField(
                text, { text = it.take(ASK_MAX) }, "Ask about your money",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailing = { SwButton("Send", send, style = ButtonStyle.Text, compact = true, enabled = !state.busy) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Starters(onAsk: (String) -> Unit) {
    val c = SpendWiseTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your figures are added up on this phone from your own entries. Advice is written by AI and only sees totals.", style = SwType.body, color = c.textMuted)
        Group("Your numbers", ASSISTANT_NUMBER_PROMPTS, onAsk)
        Group("Advice", ASSISTANT_ADVICE_PROMPTS, onAsk)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, prompts: List<String>, onAsk: (String) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = SwType.labelStrong, color = SpendWiseTheme.colors.text, modifier = Modifier.semantics { heading() })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        prompts.forEach { q -> SwChip(q, false, { onAsk(q) }) }
    }
}

@Composable
private fun ChipRow(labels: List<String>, onPick: (String) -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { l -> SwChip(l, false, { onPick(l) }) }
    }

/** A calculated answer: the figure first, then what it's made of and how it was worked out. */
@Composable
private fun CardView(card: FinCard, onOpenHistory: (AskHistory) -> Unit) {
    val c = SpendWiseTheme.colors
    Column(
        Modifier.fillMaxWidth().background(c.surface, Radius.block).border(1.dp, c.line, Radius.block).padding(16.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(card.title, style = SwType.label, color = c.textMuted)
        Text(card.headline, style = SwType.title, color = if (card.up) c.negative else c.text)
        card.caption?.let { Text(it, style = SwType.body, color = c.text) }
        card.changeText?.let { Text(it, style = SwType.bodyStrong, color = if (card.up) c.negative else c.text) }
        if (card.facts.isNotEmpty()) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            card.facts.forEach { f -> Line(f.label, f.value) }
        }
        if (card.rows.isNotEmpty()) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            card.rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = SwType.body, color = c.text)
                        r.meta?.let { Text(it, style = SwType.caption, color = c.textMuted) }
                    }
                    Text(r.amount, style = SwType.bodyStrong, color = c.text, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Text(card.basis, style = SwType.caption, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
        val link = card.history
        if (link != null && card.moreRows > 0) SwButton("See all in History", { onOpenHistory(link) }, style = ButtonStyle.Text, compact = true)
        else if (link != null) SwButton("Open in History", { onOpenHistory(link) }, style = ButtonStyle.Text, compact = true)
    }
}

@Composable
private fun Line(label: String, value: String) = Row(Modifier.fillMaxWidth()) {
    val c = SpendWiseTheme.colors
    Text(label, style = SwType.label, color = c.textMuted, modifier = Modifier.weight(1f))
    Text(value, style = SwType.labelStrong, color = c.text, modifier = Modifier.padding(start = 12.dp))
}

@Composable
private fun UserMessage(text: String) = Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
    Text(
        text, style = SwType.body, color = SpendWiseTheme.colors.text,
        modifier = Modifier.padding(start = 48.dp).background(SpendWiseTheme.colors.surface, Radius.control).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** renderMarkdown, drawn: headings, lists and paragraphs; the text is shown as text, never markup. */
@Composable
private fun Answer(text: String) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val c = SpendWiseTheme.colors
    markdownBlocks(text).forEach { b ->
        when (b) {
            is MdBlock.Heading -> Text(styled(b.text), style = SwType.bodyStrong, color = c.text)
            is MdBlock.Paragraph -> Text(styled(b.lines.joinToString("\n")), style = SwType.body, color = c.text)
            is MdBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                b.items.forEachIndexed { i, item ->
                    Row {
                        Text(if (b.ordered) "${i + 1}." else "•", style = SwType.body, color = c.textMuted, modifier = Modifier.padding(end = 8.dp))
                        Text(styled(item), style = SwType.body, color = c.text)
                    }
                }
            }
        }
    }
}

private fun styled(s: String): AnnotatedString = buildAnnotatedString {
    s.forEach { c ->
        when (c) {
            BOLD_ON -> pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
            ITALIC_ON -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            BOLD_OFF, ITALIC_OFF -> runCatching { pop() }
            else -> append(c)
        }
    }
}

/** showConsentDialog: once per device before the AI is used. */
@Composable
private fun ConsentDialog(onAnswer: (Boolean) -> Unit) = AlertDialog(
    onDismissRequest = { onAnswer(false) },
    title = { Text("Use the AI?") },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your numbers are worked out on your device. For advice, SpendWise sends the AI your question and totals by category and dates. A question the app can't read by itself is sent on its own.")
            Text(
                "Your entries, descriptions, notes and people's names are never sent. Without the AI, your figures still work.",
                color = SpendWiseTheme.colors.textMuted,
            )
        }
    },
    confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Continue") } },
    dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Not now") } },
)
