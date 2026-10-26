package com.spendwise.app.ui.debts

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.R
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.DebtPerson
import com.spendwise.app.domain.I_OWE
import com.spendwise.app.domain.KnownPerson
import com.spendwise.app.domain.THEY_OWE
import com.spendwise.app.domain.balance
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
import com.spendwise.app.ui.components.PersonSuggestions
import com.spendwise.app.ui.expenses.DateField
import com.spendwise.app.ui.expenses.FormMessageText
import com.spendwise.app.ui.format.formatMoney
import com.spendwise.app.ui.theme.SpendWiseTheme
import java.time.LocalDate

/** Debts (spec §1.5): net balance, the add form, by person, open and settled debts. */
@Composable
fun DebtsScreen(vm: DebtsViewModel = hiltViewModel()) {
    val page by vm.page.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val formMessage by vm.formMessage.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val filterKey by vm.filter.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSettled by rememberSaveable { mutableStateOf(false) }
    val fmt = { n: Double -> formatMoney(n, currency) }
    val today = LocalDate.now()
    val ready = page as? DebtsPage.Ready

    LaunchedEffect(toast) {
        val text = toast ?: return@LaunchedEffect
        vm.toastShown()
        snackbar.showSnackbar(text)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("Debts", style = MaterialTheme.typography.headlineSmall) }
            item { Hero(ready, fmt) }
            item { Text("Add a debt", style = MaterialTheme.typography.titleMedium) }
            item { AddForm(form, vm, ready?.known.orEmpty(), currency) }
            item { FormMessageText(formMessage) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::clear, modifier = Modifier.weight(1f)) { Text("Clear") }
                    Button(onClick = vm::add, modifier = Modifier.weight(1f)) { Text("Add debt") }
                }
            }
            when (val p = page) {
                DebtsPage.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is DebtsPage.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = vm::retry) { Text("Try again") }
                    }
                }
                is DebtsPage.Ready -> {
                    val (person, open) = vm.open(p)
                    if (p.showPeople) {
                        item { SectionTitle("By person", p.people.size.toString()) }
                        items(p.people, key = { "p:" + it.key }) { PersonRow(it, it.key == person?.key, fmt) { vm.toggleFilter(it.key) } }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (person != null) "Open with ${person.name}" else "Open", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (open.isNotEmpty()) Text(open.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (person != null) TextButton(onClick = vm::clearFilter) { Text("Show all") }
                        }
                    }
                    if (open.isEmpty()) item {
                        Text("No open debts. Add one above when someone borrows or lends.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(open, key = { "o:" + it.id }) { d ->
                        DebtRow(d, openMeta(d, today, fmt), fmt) { OpenActions(d, vm) }
                    }
                    if (p.settled.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable(onClickLabel = if (showSettled) "Hide" else "Show") { showSettled = !showSettled }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Settled · ${p.settled.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(fmt(p.settled.sumOf { it.amount }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (showSettled) "  Hide" else "  Show", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (showSettled) items(p.settled, key = { "s:" + it.id }) { d ->
                            DebtRow(d, settledMeta(d, today), fmt) {
                                if (!d.shared && !d.pending) TextButton(onClick = { vm.askReopen(d.id) }) { Text("Reopen") }
                            }
                        }
                    }
                }
            }
        }
    }

    PayDialogHost(vm, fmt, today)
    ConfirmDialogs(vm, fmt, today)
}

@Composable
private fun Hero(page: DebtsPage.Ready?, fmt: (Double) -> String) {
    val s = page?.summary
    val owed = MaterialTheme.colorScheme.error
    val moneyIn = SpendWiseTheme.moneyColors.moneyIn
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Net balance", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (s == null) "—" else fmt(Math.abs(s.net)),
                    style = MaterialTheme.typography.displaySmall,
                    color = when { s == null || s.net == 0.0 -> MaterialTheme.colorScheme.onSurface; s.net > 0 -> moneyIn; else -> owed },
                )
                if (s != null) Text(s.netWord, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (s != null) Text(s.sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Owed to you", s?.owedToYou, s?.owedCount, moneyIn, fmt, Modifier.weight(1f))
            Tile("You owe", s?.youOwe, s?.oweCount, owed, fmt, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(label: String, amount: Double?, count: Int?, color: Color, fmt: (Double) -> String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (amount == null) "—" else fmt(amount), style = MaterialTheme.typography.titleLarge, color = color)
        if (count != null) Text("$count open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddForm(form: DebtForm, vm: DebtsViewModel, known: List<KnownPerson>, currency: Currency) {
    val types = listOf(THEY_OWE to "They owe me", I_OWE to "I owe them")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { i, (type, label) ->
                SegmentedButton(selected = form.type == type, onClick = { vm.setType(type) }, shape = SegmentedButtonDefaults.itemShape(i, types.size)) { Text(label) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(form.person, vm::setPerson, label = { Text("Person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PersonSuggestions(form.person, form.email, known, vm::pickPerson)
        }
        if (vm.canShare) {
            OutlinedTextField(
                form.email, vm::setEmail, label = { Text("Their email (optional)") }, singleLine = true,
                supportingText = { Text("If they use SpendWise with this email, it shows in their Debts too.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            form.amount, vm::setAmount, label = { Text("Amount (${currency.symbol})") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        DateField(form.date, vm::setDate)
        OutlinedTextField(form.notes, vm::setNotes, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionTitle(title: String, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonRow(p: DebtPerson, on: Boolean, fmt: (Double) -> String, onClick: () -> Unit) {
    val balance = p.balance(fmt)
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (on) "Show all" else "Show only theirs", onClick = onClick)
                .semantics { contentDescription = "${p.name}: $balance. ${if (on) "Showing only theirs" else "Show only theirs"}" }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(p.sub(fmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt(Math.abs(p.net)),
                    color = if (p.net > 0) SpendWiseTheme.moneyColors.moneyIn else if (p.net < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (p.net > 0) "owes you" else if (p.net < 0) "you owe" else "square",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
    }
}

/** One row: who, which way and when, the amount (what's left while it's open), then the row's actions. */
@Composable
private fun DebtRow(d: Debt, meta: String, fmt: (Double) -> String, actions: @Composable () -> Unit) {
    val paid = debtPaid(d.paid)
    val part = !d.settled && paid > 0
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.person, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (part) {
                    Text("${fmt(paid)} paid back", style = MaterialTheme.typography.bodySmall)
                    PaidBar(paid, d.amount)
                }
                if (d.notes.isNotEmpty()) {
                    Text(d.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt(if (d.settled) d.amount else d.left()),
                    color = if (d.type == THEY_OWE) SpendWiseTheme.moneyColors.moneyIn else MaterialTheme.colorScheme.error,
                )
                if (part) Text("left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { actions() }
        HorizontalDivider()
    }
}

@Composable
private fun PaidBar(paid: Double, amount: Double) {
    val share = if (amount > 0) minOf(paid / amount, 1.0).toFloat() else 0f
    LinearProgressIndicator(
        progress = { share },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = "Paid back ${Math.round(share * 100)}%" },
        drawStopIndicator = {},
    )
}

/** Only what the web lets each party do: the owner records payments and deletes; the other person says what was paid. */
@Composable
private fun OpenActions(d: Debt, vm: DebtsViewModel) {
    when {
        d.pending -> Unit // no server record to update until it syncs
        d.shared -> if (d.claimDate.isNotEmpty()) {
            TextButton(onClick = { vm.undoClaim(d.id) }) { Text("Undo") }
        } else {
            TextButton(onClick = { vm.openPay(d.id) }) { Text(if (d.type == I_OWE) "I paid" else "I got paid") }
        }
        d.claimDate.isNotEmpty() -> {
            TextButton(onClick = { vm.rejectClaim(d.id) }) { Text("Not yet") }
            Button(onClick = { vm.confirmClaim(d.id) }) { Text("Confirm") }
        }
        else -> {
            TextButton(onClick = { vm.openPay(d.id) }) { Text(if (d.type == THEY_OWE) "Got paid" else "I paid") }
            IconButton(onClick = { vm.askDelete(d.id) }) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete debt with ${d.person}")
            }
        }
    }
}

@Composable
private fun PayDialogHost(vm: DebtsViewModel, fmt: (Double) -> String, today: LocalDate) {
    val dialog by vm.pay.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val pay = dialog ?: return
    val d = (page as? DebtsPage.Ready)?.all?.firstOrNull { it.id == pay.id } ?: return
    val n = parsePayAmount(pay.amount)
    val paid = debtPaid(d.paid)
    AlertDialog(
        onDismissRequest = vm::closePay,
        title = { Text(payTitle(d)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${fmt(d.left())} left", style = MaterialTheme.typography.titleMedium)
                    Text("of ${fmt(d.amount)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (paid > 0) PaidBar(paid, d.amount)
                OutlinedTextField(
                    pay.amount, vm::setPayAmount, label = { Text("Amount paid") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                DateField(pay.date, vm::setPayDate)
                payHint(d, n, fmt).takeIf { it.isNotEmpty() }?.let { Text(it) }
                if (d.paid.isNotEmpty()) {
                    Text("Paid so far", style = MaterialTheme.typography.titleSmall)
                    d.paid.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(shortDay(p.date, today), modifier = Modifier.weight(1f))
                            Text(fmt(p.amount))
                            if (!d.shared) TextButton(onClick = { vm.removePayment(i) }) { Text("Remove") }
                        }
                    }
                }
                pay.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = vm::savePay) { Text(payButton(d, n)) } },
        dismissButton = { TextButton(onClick = vm::closePay) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialogs(vm: DebtsViewModel, fmt: (Double) -> String, today: LocalDate) {
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    val reopening by vm.reopening.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete this debt?") },
            text = { Text("This can't be undone. To close a debt that was paid back, settle it instead.") },
            confirmButton = {
                Button(onClick = vm::confirmDelete) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancel") } },
        )
    }
    val d = (page as? DebtsPage.Ready)?.all?.firstOrNull { it.id == reopening }
    if (reopening != null) {
        AlertDialog(
            onDismissRequest = vm::cancelReopen,
            title = { Text("Reopen this debt?") },
            text = { Text(if (d != null) reopenMessage(d, today, fmt) else "It moves back to Open.") },
            confirmButton = { Button(onClick = vm::confirmReopen) { Text("Reopen") } },
            dismissButton = { TextButton(onClick = vm::cancelReopen) { Text("Cancel") } },
        )
    }
}
