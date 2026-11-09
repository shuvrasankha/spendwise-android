package com.spendwise.app.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spendwise.app.domain.CARD_COLOURS
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.Card
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.ListRow
import com.spendwise.app.ui.components.Metric
import com.spendwise.app.ui.components.SectionHeader
import com.spendwise.app.ui.components.SegmentedControl
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.SwSheet
import com.spendwise.app.ui.components.Toast
import com.spendwise.app.ui.components.show
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatNumber
import com.spendwise.app.ui.format.plural
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** More › Cards: what each saved card spent this month and last; tap one to see its expenses, rename or remove it. */
@Composable
fun CardsScreen(onBack: () -> Unit, onExpenses: (String) -> Unit, vm: CardsViewModel = hiltViewModel()) {
    val page by vm.page.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val c = SpendWiseTheme.colors
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        vm.noticeShown()
        snackbar.show(Toast(n, error = true))
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf<Card?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Spacing.section)) {
            item { SubPageTopBar("Cards", onBack) }
            val ready = page as? CardsPage.Ready
            item {
                val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.US)
                Metric(
                    "$month on cards", currency.symbol, if (ready == null) "—" else formatNumber(ready.spends.sumOf { it.thisMonth }, currency),
                    sub = ready?.let { "${formatAmount(it.spends.sumOf { s -> s.lastMonth }, currency)} last month" }, icon = SwIcons.CreditCard,
                )
            }
            item {
                Box(Modifier.padding(horizontal = Spacing.gutter, vertical = 24.dp)) {
                    SwButton("Add card", { adding = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, leading = SwIcons.Plus)
                }
            }
            item { SectionHeader("Your cards") }
            when (val p = page) {
                CardsPage.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.text) }
                }
                is CardsPage.Failed -> item {
                    Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.message, style = SwType.body, color = c.negative)
                        SwButton("Try again", vm::retry, style = ButtonStyle.Secondary)
                    }
                }
                is CardsPage.Ready -> {
                    if (p.spends.isEmpty()) item {
                        Text(
                            "No cards yet. Add one here, or pick Credit Card or Debit Card under Paid with when you add an expense.",
                            style = SwType.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter),
                        )
                    }
                    p.spends.forEachIndexed { i, s ->
                        item(key = s.card.key) {
                            ListRow(
                                s.card.name,
                                meta = "${s.card.short} · ${if (s.count == 0) "nothing this month" else plural(s.count, "expense", "expenses") + " this month"}",
                                amount = formatAmount(s.thisMonth, currency), sub = "${formatAmount(s.lastMonth, currency)} last month",
                                icon = SwIcons.CreditCard, iconTint = cardColor(p.colours[s.card.key]), divider = i > 0, onClick = { open = s.card },
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) AddCardSheet(onDismiss = { adding = false }) { name, type -> vm.add(name, type); adding = false }
    open?.let { card ->
        CardSheet(
            card, (page as? CardsPage.Ready)?.colours?.get(card.key), { vm.setColour(card, it) }, onDismiss = { open = null },
            onExpenses = { open = null; onExpenses(card.name) },
            onRename = { vm.rename(card, it); open = null },
            onRemove = { vm.remove(card); open = null },
        )
    }
}

private val TYPES = listOf("Credit Card", "Debit Card")

@Composable
private fun AddCardSheet(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(0) }
    SwSheet("Add card", onDismiss, footer = {
        SwButton("Save card", { onSave(name, TYPES[type]) }, Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth(), enabled = name.isNotBlank())
    }) {
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwField(name, { name = it.take(100) }, "Card name, like HDFC Millennia", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            SegmentedControl(listOf("Credit", "Debit"), type, { type = it })
            Text("Only the name is saved, never the card number.", style = SwType.label, color = SpendWiseTheme.colors.textMuted)
        }
    }
}

@Composable
private fun CardSheet(card: Card, colour: String?, onColour: (String) -> Unit, onDismiss: () -> Unit, onExpenses: () -> Unit, onRename: (String) -> Unit, onRemove: () -> Unit) {
    var name by rememberSaveable(card.key) { mutableStateOf(card.name) }
    val muted = SpendWiseTheme.colors.textMuted
    SwSheet("${card.name} · ${card.short}", onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwButton("See its expenses", onExpenses, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            Text("Colour${colour?.let { " · $it" }.orEmpty()}", style = SwType.labelStrong, color = muted)
            ColourPicker(colour, onColour)
            SwField(name, { name = it.take(100) }, "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Text("Renaming changes it on every expense paid with this card. Use another card's name to merge the two.", style = SwType.label, color = muted)
            SwButton("Rename", { onRename(name) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && name.trim() != card.name)
            SwButton("Remove from list", onRemove, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
            Text("Removing only takes it off this phone's list. Its expenses keep the card's name.", style = SwType.label, color = muted)
        }
    }
}

/** One swatch per colour, each named for TalkBack; the picked one has a ring and a tick. */
@Composable
private fun ColourPicker(picked: String?, onPick: (String) -> Unit) {
    val c = SpendWiseTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CARD_COLOURS.forEach { name ->
            val on = name == picked
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .then(if (on) Modifier.border(2.dp, c.text, CircleShape) else Modifier)
                    .padding(if (on) 5.dp else 0.dp).clip(CircleShape).background(cardColor(name))
                    .selectable(on, role = Role.RadioButton, onClick = { onPick(name) })
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(SwIcons.Check, null, Modifier.size(16.dp), tint = c.bg)
            }
        }
    }
}
