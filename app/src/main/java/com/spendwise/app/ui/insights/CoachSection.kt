package com.spendwise.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.spendwise.app.domain.BOLD_OFF
import com.spendwise.app.domain.BOLD_ON
import com.spendwise.app.domain.COACH_NO_DATA
import com.spendwise.app.domain.COACH_NOTE
import com.spendwise.app.domain.CoachTopic
import com.spendwise.app.domain.ITALIC_OFF
import com.spendwise.app.domain.ITALIC_ON
import com.spendwise.app.domain.MdBlock
import com.spendwise.app.domain.markdownBlocks
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.SectionHeader
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** The AI coach: topic chips for the period on screen, or the open conversation. [glance] null: nothing to ask about. */
@Composable
internal fun CoachSection(state: CoachState, glance: Glance?, coach: CoachViewModel) {
    val c = SpendWiseTheme.colors
    val chat = state.chat
    val ready = glance != null && glance.summary.hasData
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("AI coach", action = if (chat != null) "New chat" else null, onAction = { if (!state.busy) coach.close() })
        val note = when {
            chat != null -> "${chat.topic.title} · ${chat.label}. Answers are written by AI and can be wrong."
            glance == null || ready -> COACH_NOTE
            else -> COACH_NO_DATA
        }
        Text(note, style = SwType.label, color = c.textMuted, modifier = gutter)
        if (chat == null) {
            if (ready) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoachTopic.entries.forEach { t -> SwChip(t.chip, false, { if (!state.busy) coach.start(t, glance!!.summary, glance.range) }) }
            }
            return@Column
        }
        Column(gutter.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            chat.history.forEach { m -> if (m.role == "user") UserMessage(m.content) else Answer(m.content) }
            if (state.busy) Text("The AI coach is typing…", style = SwType.label, color = c.textMuted)
            state.error?.let { e ->
                Column {
                    Text(e, style = SwType.body, color = c.negative)
                    SwButton("Try again", coach::retry, style = ButtonStyle.Text, compact = true)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chat.topic.suggestions.forEach { q -> SwChip(q, false, { if (!state.busy) coach.send(q) }) }
        }
        var text by rememberSaveable { mutableStateOf("") }
        val send = { coach.send(text); if (!state.busy) text = "" }
        Box(gutter) {
            SwField(
                text, { text = it }, "Ask about your numbers",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailing = { SwButton("Send", send, style = ButtonStyle.Text, compact = true, enabled = !state.busy) },
            )
        }
    }
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
internal fun ConsentDialog(onAnswer: (Boolean) -> Unit) = AlertDialog(
    onDismissRequest = { onAnswer(false) },
    title = { Text("Use the AI?") },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your numbers are worked out on your device. To understand a question, SpendWise sends the AI only the question; for the coach's advice, only totals by category and dates.")
            Text(
                "Your entries, descriptions, notes and people's names are never sent. Without the AI, common questions are still answered on your device.",
                color = SpendWiseTheme.colors.textMuted,
            )
        }
    },
    confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Continue") } },
    dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Not now") } },
)
