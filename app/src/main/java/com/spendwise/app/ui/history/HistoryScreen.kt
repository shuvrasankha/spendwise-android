package com.spendwise.app.ui.history

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Entry
import com.spendwise.app.domain.HistoryRange
import com.spendwise.app.domain.HistorySort
import com.spendwise.app.domain.meta
import com.spendwise.app.ui.dashboard.Picker
import com.spendwise.app.ui.expenses.PickDateDialog
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.format.formatMoney
import com.spendwise.app.ui.format.plural
import com.spendwise.app.ui.theme.SpendWiseTheme

/** History (spec §1.6): every expense or income entry, filtered, grouped by day, with CSV export (§2.6). */
@Composable
fun HistoryScreen(
    onEditExpense: (String) -> Unit,
    onEditIncome: (String) -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    history: HistoryViewModel = hiltViewModel(),
) {
    val state by history.state.collectAsStateWithLifecycle()
    val query by history.query.collectAsStateWithLifecycle()
    val currency by history.currency.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ShareWhenReady(history)
    val message by history.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        history.messageShown()
        snackbar.showSnackbar(text)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = history::openExport, enabled = state is HistoryState.Ready) { Text("Export") }
                }
            }
            when (val s = state) {
                HistoryState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is HistoryState.Failed -> item {
                    Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = history::retry) { Text("Try again") }
                    }
                }
                is HistoryState.Ready -> {
                    val v = s.view
                    item { Filters(v, query, history) }
                    item { Summary(v, currency, history) }
                    when {
                        v.allCount == 0 -> item {
                            val income = v.filters.tab == HistoryTab.INCOME
                            Column(Modifier.padding(vertical = 24.dp)) {
                                Text(if (income) "No income recorded yet." else "No expenses yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = if (income) onAddIncome else onAddExpense, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (income) "Add income" else "Add an expense")
                                }
                            }
                        }
                        v.count == 0 -> item {
                            Column(Modifier.padding(vertical = 24.dp)) {
                                Text("Nothing matches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = history::clearFilters, contentPadding = PaddingValues(0.dp)) { Text("Clear filters") }
                            }
                        }
                    }
                    items(v.items, key = { item ->
                        when (item) {
                            is HistoryItem.Day -> "day-" + item.date
                            is HistoryItem.Row -> (if (item.entry is Entry.Earned) "inc-" else "exp-") + item.entry.id
                        }
                    }) { item ->
                        when (item) {
                            is HistoryItem.Day -> DayHeading(item, currency)
                            is HistoryItem.Row -> HistoryRow(item, currency) {
                                when (val e = item.entry) {
                                    is Entry.Spent -> onEditExpense(e.id)
                                    is Entry.Earned -> onEditIncome(e.id)
                                }
                            }
                        }
                    }
                    if (v.left > 0) item {
                        OutlinedButton(onClick = history::showMore, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text("Show ${minOf(v.left, HISTORY_STEP)} more")
                        }
                    }
                }
            }
        }
    }
    ExportDialogHost(history, currency)
}

@Composable
private fun Filters(v: HistoryView, query: String, vm: HistoryViewModel) {
    val f = v.filters
    val focus = LocalFocusManager.current
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            HistoryTab.entries.forEachIndexed { i, tab ->
                SegmentedButton(
                    selected = f.tab == tab,
                    onClick = { vm.selectTab(tab) },
                    shape = SegmentedButtonDefaults.itemShape(i, HistoryTab.entries.size),
                ) { Text(tab.label) }
            }
        }
        OutlinedTextField(
            query, vm::setQuery,
            placeholder = { Text("Search, or #tag") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            trailingIcon = if (query.isEmpty()) null else ({ TextButton(onClick = { vm.setQuery("") }) { Text("Clear") } }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(f.range.label, HistoryRange.entries.map { it to it.label }, vm::selectRange)
            Picker(f.sort.label, HistorySort.entries.map { it to it.label }, vm::selectSort)
        }
        if (f.range == HistoryRange.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { picking = "from" }, label = { Text("From ${if (f.customFrom.isEmpty()) "start" else formatDate(f.customFrom)}") })
                AssistChip(onClick = { picking = "to" }, label = { Text("To ${if (f.customTo.isEmpty()) "today" else formatDate(f.customTo)}") })
            }
        }
        if (v.kinds.isNotEmpty()) {
            ChipRow(v.kinds, selected = { it in f.kinds }, label = { it }, onToggle = vm::toggleKind)
        }
        if (v.tags.isNotEmpty()) {
            ChipRow(v.tags, selected = { it.lowercase() == v.searchedTag }, label = { "#$it" }, onToggle = vm::toggleTag)
        }
    }
    when (picking) {
        "from" -> PickDateDialog(f.customFrom, vm::setCustomFrom, onDismiss = { picking = null })
        "to" -> PickDateDialog(f.customTo, vm::setCustomTo, onDismiss = { picking = null })
    }
}

/** One scrolling line of chips, so a long list doesn't push the results off screen. */
@Composable
private fun ChipRow(values: List<String>, selected: (String) -> Boolean, label: (String) -> String, onToggle: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected = selected(value), onClick = { onToggle(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun Summary(v: HistoryView, currency: Currency, vm: HistoryViewModel) {
    if (v.count == 0) return
    val tab = v.filters.tab
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${v.count} ${if (v.count == 1) tab.one else tab.many} · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoney(v.total, currency), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (v.filtered) TextButton(onClick = vm::clearFilters) { Text("Clear filters") }
        }
        if (v.split.isNotEmpty()) {
            Text(
                v.split.joinToString(" · ") { (k, amount) -> "$k ${formatMoney(amount, currency)}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayHeading(day: HistoryItem.Day, currency: Currency) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp).semantics { heading() }) {
        Text(day.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(formatMoney(day.total, currency), style = MaterialTheme.typography.titleSmall)
    }
    HorizontalDivider()
}

@Composable
private fun HistoryRow(row: HistoryItem.Row, currency: Currency, onEdit: () -> Unit) {
    val e = row.entry
    val income = e is Entry.Earned
    val meta = (if (row.showDate) listOf(formatDate(e.date)) else emptyList()) + e.meta()
    // An entry waiting to sync has no server record yet, so it can't be edited (spec §1.6)
    val modifier = if (e.pending) Modifier else Modifier.clickable(onClickLabel = "Edit", onClick = onEdit)
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (e.notes.isNotEmpty()) {
                Text(e.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // Income reads "+₹…" in green; the sign says it as well as the colour
        Text(
            (if (income) "+" else "") + formatMoney(e.amount, currency),
            color = if (income) SpendWiseTheme.moneyColors.moneyIn else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExportDialogHost(vm: HistoryViewModel, currency: Currency) {
    val summary by vm.export.collectAsStateWithLifecycle()
    val s = summary ?: return
    val d = s.dialog
    val state by vm.state.collectAsStateWithLifecycle()
    val tab = (state as? HistoryState.Ready)?.view?.filters?.tab ?: HistoryTab.EXPENSES
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val empty = s.shownCount == 0 && s.bothCount == 0

    AlertDialog(
        onDismissRequest = vm::closeExport,
        title = { Text("Export") },
        text = {
            // Scrolls, so the whole dialog stays reachable on a small screen
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pick the dates and what goes in the file.")
                Picker(d.range.label, HistoryRange.entries.map { it to it.label }) { r -> vm.setExport { it.copy(range = r) } }
                if (d.range == HistoryRange.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { picking = "from" }, label = { Text("From ${formatDate(d.customFrom)}") })
                        AssistChip(onClick = { picking = "to" }, label = { Text("To ${formatDate(d.customTo)}") })
                    }
                }
                val shownTitle = if (s.filters.isNotEmpty()) "What's shown · ${plural(s.shownCount, tab.one, tab.many)}" else "All ${tab.many} · ${s.shownCount}"
                ExportOption(
                    shownTitle, (listOf(formatMoney(s.shownTotal, currency)) + s.filters).joinToString(" · "),
                    selected = d.what == ExportWhat.SHOWN, enabled = s.shownCount > 0,
                ) { vm.setExport { it.copy(what = ExportWhat.SHOWN) } }
                ExportOption(
                    "Expenses and income", "${plural(s.bothCount, "entry", "entries")}, without search or chips",
                    selected = d.what == ExportWhat.BOTH, enabled = s.bothCount > 0,
                ) { vm.setExport { it.copy(what = ExportWhat.BOTH) } }
                if (empty) Text("Nothing in these dates. Pick another time frame.", color = MaterialTheme.colorScheme.error)
                Row(
                    Modifier.fillMaxWidth().selectable(d.total, role = Role.Checkbox) { vm.setExport { it.copy(total = !it.total) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = d.total, onCheckedChange = null)
                    Text("Add totals at the end", Modifier.padding(start = 8.dp))
                }
                Text("A CSV file, for Excel, Numbers or Google Sheets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = vm::runExport, enabled = !empty) { Text("Share CSV") } },
        dismissButton = { TextButton(onClick = vm::closeExport) { Text("Cancel") } },
    )
    when (picking) {
        "from" -> PickDateDialog(d.customFrom, { date -> vm.setExport { it.copy(customFrom = date) } }, onDismiss = { picking = null })
        "to" -> PickDateDialog(d.customTo, { date -> vm.setExport { it.copy(customTo = date) } }, onDismiss = { picking = null })
    }
}

@Composable
private fun ExportOption(title: String, sub: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Text(title, fontWeight = FontWeight.SemiBold, color = color)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Opens the share sheet for a finished export. No storage permission: the file is granted to the target app only. */
@Composable
private fun ShareWhenReady(vm: HistoryViewModel) {
    val file by vm.share.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(file) {
        val f = file ?: return@LaunchedEffect
        vm.shareShown()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, f.uri)
            putExtra(Intent.EXTRA_TITLE, f.name)
            clipData = ClipData.newRawUri(f.name, f.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, f.name))
    }
}
