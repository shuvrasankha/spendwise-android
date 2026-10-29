package com.spendwise.app.ui.invest

import com.spendwise.app.ui.components.Panel
import com.spendwise.app.ui.components.show
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.HOLDING_KINDS
import com.spendwise.app.domain.INVEST_PAYMENTS
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
import com.spendwise.app.ui.dashboard.Picker
import com.spendwise.app.ui.expenses.DateField
import com.spendwise.app.ui.expenses.FormMessageText
import com.spendwise.app.ui.format.formatMoney
import com.spendwise.app.ui.format.formatWhole
import com.spendwise.app.ui.format.plural
import com.spendwise.app.ui.theme.SpendWiseTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Investments (spec §1.8): what it's all worth, Put money in, holdings, and what was put in recently. */
@Composable
fun InvestScreen(onAllHistory: () -> Unit, vm: InvestViewModel = hiltViewModel()) {
    val page by vm.page.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val formMessage by vm.formMessage.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val chosenId by vm.chosenId.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val whole = { n: Double -> formatWhole(n, currency) }
    val today = LocalDate.now()

    LaunchedEffect(toast) {
        val text = toast ?: return@LaunchedEffect
        vm.toastShown()
        snackbar.show(text)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Investments", style = MaterialTheme.typography.headlineSmall) }
            when (val p = page) {
                InvestPage.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is InvestPage.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = vm::retry) { Text("Try again") }
                    }
                }
                is InvestPage.Ready -> {
                    item { Panel { Hero(p, debts, whole) } }
                    item {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Put money in", style = MaterialTheme.typography.titleMedium)
                                PutMoneyIn(p, form, vm, currency.symbol, chosenId)
                                FormMessageText(formMessage)
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Holdings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (p.holdings.isNotEmpty()) Text(plural(p.holdings.size, "holding", "holdings"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { vm.newHolding() }) { Text("New holding") }
                        }
                    }
                    if (p.portfolio.rows.isEmpty() && p.portfolio.other.isEmpty()) item {
                        Text("A SIP, an FD, your PPF: add each one as a holding to see what it's worth.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(p.portfolio.rows, key = { "h:" + it.holding.id }) { r ->
                        Row2(
                            title = r.holding.name, meta = r.sub(p.goals, whole), amount = whole(r.value), under = r.gainText(whole),
                            underColor = when { r.gain == null -> null; r.gain >= 0 -> SpendWiseTheme.moneyColors.moneyIn; else -> MaterialTheme.colorScheme.error },
                            onClickLabel = "Edit",
                        ) { vm.editHolding(r.holding.id) }
                    }
                    if (p.portfolio.other.isNotEmpty()) item {
                        // Entries from before holdings, or from the expense form, that match no holding
                        Row2("Not in a holding", "${plural(p.portfolio.other.size, "entry", "entries")} · tap one below to sort it", whole(p.portfolio.otherInvested))
                    }
                    item {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Put in recently", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = onAllHistory) { Text("All in History") }
                        }
                    }
                    if (p.entries.isEmpty()) item {
                        Text("Nothing put in yet. Pick a holding above and add the first amount.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(p.entries.take(INVEST_RECENT_COUNT), key = { "e:" + it.id }) { e ->
                        Row2(
                            entryTitle(e, p.holdings), entryMeta(e, p.holdings, today), formatMoney(e.amount, currency),
                            onClickLabel = if (e.pending) null else "Edit",
                        ) { vm.openEntry(e.id) }
                    }
                }
            }
        }
    }

    HoldingDialogHost(vm, today)
    EntryDialogHost(vm, currency)
}

@Composable
private fun Hero(p: InvestPage.Ready, debts: List<Debt>, whole: (Double) -> String) {
    val pf = p.portfolio
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("All investments", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(whole(if (pf.isEmpty(p.holdings)) 0.0 else pf.value), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("worth now", modifier = Modifier.padding(bottom = 6.dp))
        }
        if (pf.isEmpty(p.holdings)) {
            Muted("Add a holding below, then the money you put in.")
            return
        }
        Muted(pf.worthSub(whole))
        Muted(pf.monthSub(LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.US), whole))
        val a = afterDebts(pf.value, debts)
        if (a.shown()) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("After debts")
                Text(a.totalText(whole), style = MaterialTheme.typography.titleMedium)
            }
            Muted(a.note(whole))
        }
    }
}

@Composable
private fun Muted(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)

// [chosenId] is passed in, not read from the ViewModel here: as an input it redraws the chips when it changes
@Composable
private fun PutMoneyIn(p: InvestPage.Ready, form: InvestForm, vm: InvestViewModel, symbol: String, chosenId: String?) {
    val chosen = vm.chosen(p, chosenId)
    val sip = p.portfolio.rows.firstOrNull { it.holding.id == chosen?.id }?.sip
    val repeat = form.repeat ?: (sip != null)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.holdings.forEach { h -> FilterChip(selected = h.id == chosen?.id, onClick = { vm.choose(h.id) }, label = { Text(h.name) }) }
            FilterChip(selected = false, onClick = { vm.newHolding() }, label = { Text("+ New holding") })
        }
        OutlinedTextField(
            form.amount, vm::setAmount, label = { Text("Amount ($symbol)") }, singleLine = true,
            // An empty amount uses the SIP's, so it shows as the hint
            placeholder = { Text(sip?.let { plain(it.amount) } ?: "0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        DateField(form.date, vm::setDate)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Paid with", style = MaterialTheme.typography.titleSmall)
            Picker(INVEST_PAYMENTS.first { it.first == form.payment }.second, INVEST_PAYMENTS, vm::setPayment)
        }
        OutlinedTextField(form.notes, vm::setNotes, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().toggleable(value = repeat, role = Role.Switch, onValueChange = vm::setRepeat),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Every month (SIP)")
                Text("The Dashboard offers it again when it's due", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = repeat, onCheckedChange = null)
        }
        Button(onClick = vm::add, modifier = Modifier.fillMaxWidth()) { Text("Add") }
    }
}

/** A list row: title and meta on the left, an amount (and a line under it) on the right. */
@Composable
private fun Row2(
    title: String, meta: String, amount: String, under: String? = null,
    underColor: Color? = null, onClickLabel: String? = null, onClick: () -> Unit = {},
) {
    Panel(onClick = onClick.takeIf { onClickLabel != null }, onClickLabel = onClickLabel) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount)
                if (under != null) {
                    Text(under, style = MaterialTheme.typography.bodySmall, color = underColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HoldingDialogHost(vm: InvestViewModel, today: LocalDate) {
    val dialog by vm.holdingDialog.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val d = dialog ?: return
    val p = page as? InvestPage.Ready ?: return
    val was = p.holdings.firstOrNull { it.id == d.id }
    if (d.confirmDelete && was != null) {
        AlertDialog(
            onDismissRequest = vm::cancelDeleteHolding,
            text = { Text("Delete \"${was.name}\"? The money you put in stays in History, under Not in a holding.") },
            confirmButton = { Button(onClick = vm::deleteHolding) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDeleteHolding) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = vm::closeHolding,
        title = { Text(if (was != null) "Edit holding" else "New holding") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(d.name, { v -> vm.setHolding { it.copy(name = v.take(60)) } }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Kind", style = MaterialTheme.typography.titleSmall)
                    Picker(kindOf(d.kind).label, HOLDING_KINDS.map { it.id to it.label }) { k -> vm.setHolding { it.copy(kind = k) } }
                }
                OutlinedTextField(
                    d.value, { v -> vm.setHolding { it.copy(value = v) } }, label = { Text("Worth now (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (was?.value != null && !was.valueDate.isNullOrEmpty()) "Last updated ${shortDay(was.valueDate, today)}. Money put in since then is added to it."
                    else "Leave it empty to count what you put in. Update it now and then to see your gain.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (p.goals.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Saving for", style = MaterialTheme.typography.titleSmall)
                        val options = listOf("" to "No goal") + p.goals.map { it.id to it.name }
                        Picker(options.first { it.first == d.goalId }.second, options) { g -> vm.setHolding { it.copy(goalId = g) } }
                        Text("Its worth counts towards that goal on the Dashboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                d.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (was != null) OutlinedButton(onClick = vm::askDeleteHolding) { Text("Delete holding") }
            }
        },
        confirmButton = { Button(onClick = vm::saveHolding) { Text("Save") } },
        dismissButton = { TextButton(onClick = vm::closeHolding) { Text("Cancel") } },
    )
}

private const val NEW_HOLDING = "__new"

@Composable
private fun EntryDialogHost(vm: InvestViewModel, currency: Currency) {
    val dialog by vm.entryDialog.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val d = dialog ?: return
    val p = page as? InvestPage.Ready ?: return
    val e = p.entries.firstOrNull { it.id == d.id } ?: return
    if (d.confirmDelete) {
        AlertDialog(
            onDismissRequest = vm::cancelDeleteEntry,
            text = { Text("Delete ${formatMoney(e.amount, currency)} on ${shortDay(e.date, LocalDate.now())}?") },
            confirmButton = { Button(onClick = vm::deleteEntry) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDeleteEntry) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = vm::closeEntry,
        title = { Text("Money put in") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Into", style = MaterialTheme.typography.titleSmall)
                    // "New holding…" last, so an entry can be sorted even before there is a holding to put it in
                    val options = listOf("" to "Not in a holding") + p.holdings.map { it.id to it.name } + (NEW_HOLDING to "New holding…")
                    Picker(options.firstOrNull { it.first == d.holdingId }?.second ?: "Not in a holding", options) { id ->
                        if (id == NEW_HOLDING) vm.newHoldingForEntry() else vm.setEntry { it.copy(holdingId = id) }
                    }
                }
                OutlinedTextField(
                    d.amount, { v -> vm.setEntry { it.copy(amount = v) } }, label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                DateField(d.date) { v -> vm.setEntry { it.copy(date = v) } }
                d.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = vm::askDeleteEntry) { Text("Delete") }
            }
        },
        confirmButton = { Button(onClick = vm::saveEntry) { Text("Save") } },
        dismissButton = { TextButton(onClick = vm::closeEntry) { Text("Cancel") } },
    )
}
