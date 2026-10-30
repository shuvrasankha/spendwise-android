package com.spendwise.app.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.I_OWE
import com.spendwise.app.domain.THEY_OWE
import com.spendwise.app.domain.debtPaid
import com.spendwise.app.domain.left
import com.spendwise.app.domain.openMeta
import com.spendwise.app.domain.parsePayAmount
import com.spendwise.app.domain.payButton
import com.spendwise.app.domain.payHint
import com.spendwise.app.domain.payTitle
import com.spendwise.app.domain.reopenMessage
import com.spendwise.app.domain.settledMeta
import com.spendwise.app.domain.shortDay
import com.spendwise.app.domain.sub
import com.spendwise.app.ui.components.BarSegment
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.DateChips
import com.spendwise.app.ui.components.ListRow
import com.spendwise.app.ui.components.Metric
import com.spendwise.app.ui.components.SectionHeader
import com.spendwise.app.ui.components.StackedBar
import com.spendwise.app.ui.components.Stat
import com.spendwise.app.ui.components.StatGrid
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.SwSheet
import com.spendwise.app.ui.components.show
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatNumber
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.LocalDate

/** Debts (spec §1.5): net balance, a button for the New entry sheet, by person, open and settled debts. */
@Composable
fun DebtsScreen(onBack: () -> Unit, onAdd: () -> Unit, vm: DebtsViewModel = hiltViewModel()) {
    val page by vm.page.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val filterKey by vm.filter.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSettled by rememberSaveable { mutableStateOf(false) }
    val fmt = { n: Double -> formatAmount(n, currency) }
    val today = LocalDate.now()
    val c = SpendWiseTheme.colors

    LaunchedEffect(toast) {
        val text = toast ?: return@LaunchedEffect
        vm.toastShown()
        snackbar.show(text)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Spacing.section)) {
            item { SubPageTopBar("Debts", onBack) }
            val ready = page as? DebtsPage.Ready
            item {
                val s = ready?.summary
                Metric(
                    "Net balance", currency.symbol, if (s == null) "—" else formatNumber(Math.abs(s.net), currency),
                    sub = s?.let { "${it.netWord} · ${it.sub}" }, positive = s != null && s.net > 0, negative = s != null && s.net < 0,
                )
            }
            item {
                val s = ready?.summary
                Box(Modifier.padding(top = 20.dp)) {
                    StatGrid(
                        listOf(
                            Stat("Owed to you", s?.owedToYou?.let(fmt) ?: "—", SwIcons.People, if ((s?.owedToYou ?: 0.0) > 0) c.positive else Color.Unspecified, s?.owedCount?.let { "$it open" }),
                            Stat("You owe", s?.youOwe?.let(fmt) ?: "—", SwIcons.MoneyOut, if ((s?.youOwe ?: 0.0) > 0) c.negative else Color.Unspecified, s?.oweCount?.let { "$it open" }),
                        ),
                    )
                }
            }
            item { Box(Modifier.padding(horizontal = Spacing.gutter, vertical = 24.dp)) { SwButton("Add debt", onAdd, Modifier.fillMaxWidth(), leading = SwIcons.Plus) } }
            when (val p = page) {
                DebtsPage.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.text) }
                }
                is DebtsPage.Failed -> item {
                    Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.message, style = SwType.body, color = c.negative)
                        SwButton("Try again", vm::retry, style = ButtonStyle.Secondary)
                    }
                }
                is DebtsPage.Ready -> {
                    val (person, open) = vm.open(p)
                    if (p.showPeople) {
                        item { SectionHeader("By person") }
                        p.people.forEachIndexed { i, pr ->
                            item(key = "p:" + pr.key) {
                                val on = pr.key == person?.key
                                ListRow(
                                    pr.name, meta = pr.sub(fmt), amount = fmt(Math.abs(pr.net)),
                                    amountColor = if (pr.net > 0) c.positive else if (pr.net < 0) c.negative else Color.Unspecified,
                                    sub = if (pr.net > 0) "owes you" else if (pr.net < 0) "you owe" else "square",
                                    // The person the Open list is narrowed to shows a tick
                                    icon = if (on) SwIcons.Check else SwIcons.People, divider = i > 0, onClick = { vm.toggleFilter(pr.key) },
                                )
                            }
                        }
                    }
                    item(key = "open-header") {
                        Box(Modifier.padding(top = if (p.showPeople) Spacing.section else 0.dp)) {
                            SectionHeader(if (person != null) "Open with ${person.name}" else "Open", action = if (person != null) "Show all" else null, onAction = vm::clearFilter)
                        }
                    }
                    if (open.isEmpty()) item {
                        Text("No open debts. Add one when someone borrows or lends.", style = SwType.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter))
                    }
                    open.forEachIndexed { i, d -> item(key = "o:" + d.id) { DebtItem(d, openMeta(d, today, fmt), fmt, divider = i > 0) { OpenActions(d, vm) } } }
                    if (p.settled.isNotEmpty()) {
                        item(key = "settled-header") {
                            Box(Modifier.padding(top = Spacing.section)) {
                                SectionHeader("Settled · ${p.settled.size} · ${fmt(p.settled.sumOf { it.amount })}", action = if (showSettled) "Hide" else "Show", onAction = { showSettled = !showSettled })
                            }
                        }
                        if (showSettled) p.settled.forEachIndexed { i, d ->
                            item(key = "s:" + d.id) {
                                DebtItem(d, settledMeta(d, today), fmt, divider = i > 0) {
                                    if (!d.shared && !d.pending) SwButton("Reopen", { vm.askReopen(d.id) }, style = ButtonStyle.Text, compact = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    PaySheetHost(vm, fmt, today)
    ConfirmDialogs(vm, fmt, today)
}

/** One row: who, which way and when, the amount (what's left while it's open), a paid-back bar, then the row's actions. */
@Composable
private fun DebtItem(d: Debt, meta: String, fmt: (Double) -> String, divider: Boolean, actions: @Composable () -> Unit) {
    val c = SpendWiseTheme.colors
    val paid = debtPaid(d.paid)
    val part = !d.settled && paid > 0
    Column {
        ListRow(
            d.person, meta = listOf(meta, d.notes).filter { it.isNotEmpty() }.joinToString("\n"), metaLines = 3,
            amount = fmt(if (d.settled) d.amount else d.left()), amountColor = if (d.type == THEY_OWE) c.positive else c.negative,
            sub = if (part) "left" else null, icon = if (d.type == THEY_OWE) SwIcons.MoneyIn else SwIcons.MoneyOut, divider = divider,
        )
        if (part) {
            val share = if (d.amount > 0) minOf(paid / d.amount, 1.0).toFloat() else 0f
            Column(Modifier.padding(start = Spacing.gutter + 48.dp, end = Spacing.gutter, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${fmt(paid)} paid back", style = SwType.caption, color = c.textMuted)
                StackedBar(listOf(BarSegment(share, c.textMuted), BarSegment(1f - share, Color.Transparent)))
            }
        }
        Row(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Only what the web lets each party do: the owner records payments and deletes; the other person says what was paid. */
@Composable
private fun OpenActions(d: Debt, vm: DebtsViewModel) {
    when {
        d.pending -> Unit // no server record to update until it syncs
        d.shared -> if (d.claimDate.isNotEmpty()) {
            SwButton("Undo", { vm.undoClaim(d.id) }, style = ButtonStyle.Text, compact = true)
        } else {
            SwButton(if (d.type == I_OWE) "I paid" else "I got paid", { vm.openPay(d.id) }, style = ButtonStyle.Secondary, compact = true)
        }
        d.claimDate.isNotEmpty() -> {
            SwButton("Confirm", { vm.confirmClaim(d.id) }, style = ButtonStyle.Secondary, compact = true)
            SwButton("Not yet", { vm.rejectClaim(d.id) }, style = ButtonStyle.Text, compact = true)
        }
        else -> {
            SwButton("Mark as paid", { vm.openPay(d.id) }, style = ButtonStyle.Secondary, compact = true)
            SwButton("Delete", { vm.askDelete(d.id) }, style = ButtonStyle.Danger, compact = true)
        }
    }
}

/** The payment: a sheet with what's left, the amount and date, and the payments so far. */
@Composable
private fun PaySheetHost(vm: DebtsViewModel, fmt: (Double) -> String, today: LocalDate) {
    val dialog by vm.pay.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val pay = dialog ?: return
    val d = (page as? DebtsPage.Ready)?.all?.firstOrNull { it.id == pay.id } ?: return
    val c = SpendWiseTheme.colors
    val n = parsePayAmount(pay.amount)
    val paid = debtPaid(d.paid)
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    SwSheet(
        payTitle(d), vm::closePay,
        footer = { Box(gutter) { SwButton(payButton(d, n), vm::savePay, Modifier.fillMaxWidth()) } },
    ) {
        Row(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Text("${fmt(d.left())} left", style = SwType.stat, color = c.text)
            Text("of ${fmt(d.amount)}", style = SwType.label, color = c.textMuted, modifier = Modifier.padding(bottom = 2.dp))
        }
        if (paid > 0) {
            val share = if (d.amount > 0) minOf(paid / d.amount, 1.0).toFloat() else 0f
            Box(gutter) { StackedBar(listOf(BarSegment(share, c.textMuted), BarSegment(1f - share, Color.Transparent))) }
        }
        Box(gutter) {
            SwField(pay.amount, vm::setPayAmount, "Amount paid", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        DateChips(pay.date, today, vm::setPayDate)
        payHint(d, n, fmt).takeIf { it.isNotEmpty() }?.let { Text(it, style = SwType.label, color = c.textMuted, modifier = gutter) }
        if (d.paid.isNotEmpty()) {
            Column {
                SectionHeader("Paid so far")
                d.paid.forEachIndexed { i, p ->
                    Row(gutter.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(shortDay(p.date, today), style = SwType.body, color = c.text, modifier = Modifier.weight(1f))
                        Text(fmt(p.amount), style = SwType.bodyStrong, color = c.text)
                        if (!d.shared) SwButton("Remove", { vm.removePayment(i) }, style = ButtonStyle.Danger, compact = true)
                    }
                }
            }
        }
        pay.error?.let { Text(it, style = SwType.label, color = c.negative, modifier = gutter) }
    }
}

@Composable
private fun ConfirmDialogs(vm: DebtsViewModel, fmt: (Double) -> String, today: LocalDate) {
    val c = SpendWiseTheme.colors
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    val reopening by vm.reopening.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete this debt?") },
            text = { Text("This can't be undone. To close a debt that was paid back, settle it instead.") },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Delete", color = c.negative) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancel") } },
        )
    }
    val d = (page as? DebtsPage.Ready)?.all?.firstOrNull { it.id == reopening }
    if (reopening != null) {
        AlertDialog(
            onDismissRequest = vm::cancelReopen,
            title = { Text("Reopen this debt?") },
            text = { Text(if (d != null) reopenMessage(d, today, fmt) else "It moves back to Open.") },
            confirmButton = { TextButton(onClick = vm::confirmReopen) { Text("Reopen") } },
            dismissButton = { TextButton(onClick = vm::cancelReopen) { Text("Cancel") } },
        )
    }
}
