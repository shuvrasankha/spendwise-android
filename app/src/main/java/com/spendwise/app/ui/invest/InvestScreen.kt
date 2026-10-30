package com.spendwise.app.ui.invest

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.HOLDING_KINDS
import com.spendwise.app.domain.INVEST_RECENT_COUNT
import com.spendwise.app.domain.afterDebts
import com.spendwise.app.domain.entryMeta
import com.spendwise.app.domain.entryTitle
import com.spendwise.app.domain.gainText
import com.spendwise.app.domain.isEmpty
import com.spendwise.app.domain.kindOf
import com.spendwise.app.domain.monthSub
import com.spendwise.app.domain.note
import com.spendwise.app.domain.shortDay
import com.spendwise.app.domain.shown
import com.spendwise.app.domain.sub
import com.spendwise.app.domain.totalText
import com.spendwise.app.domain.worthSub
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.DateChips
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.ListRow
import com.spendwise.app.ui.components.Metric
import com.spendwise.app.ui.components.SectionHeader
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.SwSheet
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

/**
 * Investments (spec §1.8): what it's all worth, a button for the New entry sheet, holdings, and what was put in recently.
 * The Put money in confirmation is shown by the app shell, which owns the snackbar.
 */
@Composable
fun InvestScreen(onBack: () -> Unit, onAdd: () -> Unit, onAllHistory: () -> Unit, vm: InvestViewModel = hiltViewModel()) {
    val page by vm.page.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val money = { n: Double -> formatAmount(n, currency) }
    val today = LocalDate.now()
    val c = SpendWiseTheme.colors

    Scaffold(contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Spacing.section)) {
            item { SubPageTopBar("Investments", onBack) }
            when (val p = page) {
                InvestPage.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.text) }
                }
                is InvestPage.Failed -> item {
                    Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.message, style = SwType.body, color = c.negative)
                        SwButton("Try again", vm::retry, style = ButtonStyle.Secondary)
                    }
                }
                is InvestPage.Ready -> {
                    item { Hero(p, debts, currency, money) }
                    item { Box(Modifier.padding(horizontal = Spacing.gutter, vertical = 24.dp)) { SwButton("Put money in", onAdd, Modifier.fillMaxWidth(), leading = SwIcons.Plus) } }
                    item {
                        SectionHeader("Holdings", action = "New holding", onAction = { vm.newHolding() })
                        if (p.holdings.isNotEmpty()) Text(plural(p.holdings.size, "holding", "holdings"), style = SwType.label, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter))
                    }
                    if (p.portfolio.rows.isEmpty() && p.portfolio.other.isEmpty()) item {
                        Text("A SIP, an FD, your PPF: add each one as a holding to see what it's worth.", style = SwType.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter))
                    }
                    p.portfolio.rows.forEachIndexed { i, r ->
                        item(key = "h:" + r.holding.id) {
                            ListRow(
                                r.holding.name, meta = r.sub(p.goals, money), amount = money(r.value), sub = r.gainText(money),
                                icon = SwIcons.TrendingUp, divider = i > 0, onClick = { vm.editHolding(r.holding.id) },
                            )
                        }
                    }
                    if (p.portfolio.other.isNotEmpty()) item {
                        // Entries from before holdings, or from the expense form, that match no holding
                        ListRow(
                            "Not in a holding", meta = "${plural(p.portfolio.other.size, "entry", "entries")} · tap one below to sort it",
                            amount = money(p.portfolio.otherInvested), icon = SwIcons.Wallet, divider = p.portfolio.rows.isNotEmpty(),
                        )
                    }
                    item(key = "recent-header") {
                        Box(Modifier.padding(top = Spacing.section)) { SectionHeader("Put in recently", action = "All in History", onAction = onAllHistory) }
                    }
                    if (p.entries.isEmpty()) item {
                        Text("Nothing put in yet. Add a holding, then put the first amount in.", style = SwType.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter))
                    }
                    p.entries.take(INVEST_RECENT_COUNT).forEachIndexed { i, e ->
                        item(key = "e:" + e.id) {
                            ListRow(
                                entryTitle(e, p.holdings), meta = entryMeta(e, p.holdings, today), amount = money(e.amount), icon = SwIcons.Wallet, divider = i > 0,
                                onClick = if (e.pending) null else ({ vm.openEntry(e.id) }),
                            )
                        }
                    }
                }
            }
        }
    }

    HoldingSheetHost(vm, today)
    EntrySheetHost(vm, currency, today)
}

@Composable
private fun Hero(p: InvestPage.Ready, debts: List<Debt>, currency: Currency, money: (Double) -> String) {
    val c = SpendWiseTheme.colors
    val pf = p.portfolio
    val empty = pf.isEmpty(p.holdings)
    val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.US)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric(
            "All investments · worth now", currency.symbol, formatNumber(if (empty) 0.0 else pf.value, currency),
            sub = if (empty) "Add a holding, then the money you put in." else listOf(pf.worthSub(money), pf.monthSub(month, money)).joinToString("\n"),
        )
        if (!empty) {
            val a = afterDebts(pf.value, debts)
            if (a.shown()) {
                Row(Modifier.padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("After debts", style = SwType.label, color = c.textMuted)
                    Text(a.totalText(money), style = SwType.stat, color = c.text)
                }
                Text(a.note(money), style = SwType.label, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter))
            }
        }
    }
}

private const val NEW_HOLDING = "__new"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HoldingSheetHost(vm: InvestViewModel, today: LocalDate) {
    val dialog by vm.holdingDialog.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val d = dialog ?: return
    val p = page as? InvestPage.Ready ?: return
    val c = SpendWiseTheme.colors
    val was = p.holdings.firstOrNull { it.id == d.id }
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    if (d.confirmDelete && was != null) {
        AlertDialog(
            onDismissRequest = vm::cancelDeleteHolding,
            text = { Text("Delete \"${was.name}\"? The money you put in stays in History, under Not in a holding.") },
            confirmButton = { TextButton(onClick = vm::deleteHolding) { Text("Delete", color = c.negative) } },
            dismissButton = { TextButton(onClick = vm::cancelDeleteHolding) { Text("Cancel") } },
        )
        return
    }
    SwSheet(
        if (was != null) "Edit holding" else "New holding", vm::closeHolding,
        footer = {
            d.error?.let { Text(it, style = SwType.label, color = c.negative, modifier = gutter) }
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (was != null) SwButton("Delete", vm::askDeleteHolding, style = ButtonStyle.Danger)
                SwButton("Save", vm::saveHolding, Modifier.weight(1f))
            }
        },
    ) {
        Box(gutter) { SwField(d.name, { v -> vm.setHolding { it.copy(name = v.take(60)) } }, "Name") }
        FieldLabel("Kind")
        FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HOLDING_KINDS.forEach { k -> SwChip(k.label, kindOf(d.kind).id == k.id, { vm.setHolding { it.copy(kind = k.id) } }) }
        }
        Box(gutter) {
            SwField(d.value, { v -> vm.setHolding { it.copy(value = v) } }, "Worth now (optional)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Text(
            if (was?.value != null && !was.valueDate.isNullOrEmpty()) "Last updated ${shortDay(was.valueDate, today)}. Money put in since then is added to it."
            else "Leave it empty to count what you put in. Update it now and then to see your gain.",
            style = SwType.label, color = c.textMuted, modifier = gutter,
        )
        if (p.goals.isNotEmpty()) {
            FieldLabel("Saving for")
            FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("" to "No goal") + p.goals.map { it.id to it.name }).forEach { (id, name) ->
                    SwChip(name, d.goalId == id, { vm.setHolding { it.copy(goalId = id) } })
                }
            }
            Text("Its worth counts towards that goal on Home.", style = SwType.label, color = c.textMuted, modifier = gutter)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntrySheetHost(vm: InvestViewModel, currency: Currency, today: LocalDate) {
    val dialog by vm.entryDialog.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val d = dialog ?: return
    val p = page as? InvestPage.Ready ?: return
    val e = p.entries.firstOrNull { it.id == d.id } ?: return
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    if (d.confirmDelete) {
        AlertDialog(
            onDismissRequest = vm::cancelDeleteEntry,
            text = { Text("Delete ${formatAmount(e.amount, currency)} on ${shortDay(e.date, today)}?") },
            confirmButton = { TextButton(onClick = vm::deleteEntry) { Text("Delete", color = c.negative) } },
            dismissButton = { TextButton(onClick = vm::cancelDeleteEntry) { Text("Cancel") } },
        )
        return
    }
    SwSheet(
        "Money put in", vm::closeEntry,
        footer = {
            d.error?.let { Text(it, style = SwType.label, color = c.negative, modifier = gutter) }
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SwButton("Delete", vm::askDeleteEntry, style = ButtonStyle.Danger)
                SwButton("Save", vm::saveEntry, Modifier.weight(1f))
            }
        },
    ) {
        FieldLabel("Into")
        // "New holding" last, so an entry can be sorted even before there is a holding to put it in
        val options = listOf("" to "Not in a holding") + p.holdings.map { it.id to it.name } + (NEW_HOLDING to "New holding")
        FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, name) ->
                SwChip(name, id != NEW_HOLDING && d.holdingId == id, { if (id == NEW_HOLDING) vm.newHoldingForEntry() else vm.setEntry { it.copy(holdingId = id) } }, leading = if (id == NEW_HOLDING) SwIcons.Plus else null)
            }
        }
        Box(gutter) {
            SwField(d.amount, { v -> vm.setEntry { it.copy(amount = v) } }, "Amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        DateChips(d.date, today, { v -> vm.setEntry { it.copy(date = v) } })
    }
}
