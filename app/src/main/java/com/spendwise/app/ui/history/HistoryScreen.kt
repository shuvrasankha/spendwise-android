package com.spendwise.app.ui.history

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Entry
import com.spendwise.app.domain.HistoryRange
import com.spendwise.app.domain.HistorySort
import com.spendwise.app.domain.meta
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.DayHeader
import com.spendwise.app.ui.components.SkeletonBar
import com.spendwise.app.ui.components.SkeletonRows
import com.spendwise.app.ui.components.DateField
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.IconAction
import com.spendwise.app.ui.components.ListRow
import com.spendwise.app.ui.components.Metric
import com.spendwise.app.ui.components.countUp
import com.spendwise.app.ui.components.PickDateDialog
import com.spendwise.app.ui.components.SegmentedControl
import com.spendwise.app.ui.components.SettingsGroup
import com.spendwise.app.ui.components.SettingsRow
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.SwSheet
import com.spendwise.app.ui.components.TabTopBar
import com.spendwise.app.ui.components.Trailing
import com.spendwise.app.ui.components.show
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.format.formatNumber
import com.spendwise.app.ui.format.plural
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

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
    val c = SpendWiseTheme.colors
    var sheet by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    ShareWhenReady(history)
    SaveWhenReady(history)
    val message by history.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        history.messageShown()
        snackbar.show(text)
    }

    val list = rememberLazyListState()
    // Load the next 50 rows as the list nears its end, in place of the web's "Show 50 more" button.
    // Keyed on the row count, so a page that still leaves the end in view loads the next one too.
    val ready = (state as? HistoryState.Ready)?.view
    LaunchedEffect(list, ready?.items?.size, (ready?.left ?: 0) > 0) {
        if ((ready?.left ?: 0) <= 0) return@LaunchedEffect
        snapshotFlow {
            val info = list.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_AHEAD
        }.filter { it }.first()
        history.showMore()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), state = list, contentPadding = PaddingValues(bottom = Spacing.section)) {
            item {
                TabTopBar("History") {
                    if (state is HistoryState.Ready) IconAction(SwIcons.Download, "Export", history::openExport)
                }
            }
            when (val s = state) {
                HistoryState.Loading -> item(key = "skeleton") {
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Where the search box and summary will be
                        SkeletonBar(Dp.Unspecified, 48.dp, Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter))
                        SkeletonRows(8)
                    }
                }
                is HistoryState.Failed -> item {
                    Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(s.message, style = SwType.body, color = c.negative)
                        SwButton("Try again", history::retry, style = ButtonStyle.Secondary)
                    }
                }
                is HistoryState.Ready -> {
                    val v = s.view
                    val tab = v.filters.tab
                    item { Controls(v, query, history, openFilters = { sheet = true }) }
                    if (v.count > 0) item { Summary(v, currency, history) }
                    when {
                        v.allCount == 0 -> item {
                            val income = tab == HistoryTab.INCOME
                            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (income) "No income recorded yet." else "No expenses yet.", style = SwType.body, color = c.textMuted)
                                SwButton(if (income) "Add income" else "Add an expense", if (income) onAddIncome else onAddExpense, style = ButtonStyle.Secondary)
                            }
                        }
                        v.count == 0 -> item {
                            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Nothing matches.", style = SwType.body, color = c.textMuted)
                                SwButton("Clear filters", history::clearFilters, style = ButtonStyle.Secondary)
                            }
                        }
                    }
                    v.items.forEachIndexed { i, item ->
                        when (item) {
                            is HistoryItem.Day -> stickyHeader(key = "day-" + item.date) { DayHeader(item.label, formatAmount(item.total, currency)) }
                            is HistoryItem.Row -> item(key = (if (item.entry is Entry.Earned) "inc-" else "exp-") + item.entry.id) { Box(Modifier.animateItem()) {
                                val e = item.entry
                                val edit = {
                                    when (e) {
                                        is Entry.Spent -> onEditExpense(e.id)
                                        is Entry.Earned -> onEditIncome(e.id)
                                    }
                                }
                                SwipeRow(enabled = !e.pending, onEdit = edit, onDelete = { deleting = e }) {
                                    HistoryRow(item, currency, divider = i > 0 && v.items[i - 1] is HistoryItem.Row, onEdit = edit)
                                }
                            }}
                        }
                    }
                    if (v.left > 0) item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = c.textMuted, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
    if (sheet) (state as? HistoryState.Ready)?.let { FiltersSheet(it.view, history) { sheet = false } }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(if (e is Entry.Earned) "Delete this income entry?" else "Delete this expense?") },
            text = { Text("It will be removed from SpendWise on all your devices.") },
            confirmButton = { TextButton(onClick = { history.delete(e); deleting = null }) { Text("Delete", color = c.negative) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    ExportSheet(history, currency)
}

/** Rows from the end of the list at which the next page loads. */
private const val LOAD_AHEAD = 10

/** Expenses/Income, search, then Filters and the active chips. */
@Composable
private fun Controls(v: HistoryView, query: String, vm: HistoryViewModel, openFilters: () -> Unit) {
    val f = v.filters
    val focus = LocalFocusManager.current
    val active = (if (f.range != HistoryRange.ALL) 1 else 0) + (if (f.sort != HistorySort.NEWEST) 1 else 0) + f.kinds.size
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(horizontal = Spacing.gutter)) {
            SegmentedControl(HistoryTab.entries.map { it.label }, f.tab.ordinal, { vm.selectTab(HistoryTab.entries[it]) })
        }
        Box(Modifier.padding(horizontal = Spacing.gutter)) {
            SwField(
                query, vm::setQuery, "Search, or #tag", leading = SwIcons.Search,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                trailing = if (query.isEmpty()) null else ({ IconAction(SwIcons.Close, "Clear search", { vm.setQuery("") }) }),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwChip(if (active == 0) "Filters" else "Filters ($active)", false, openFilters, leading = SwIcons.Filters)
            if (f.range != HistoryRange.ALL) SwChip(f.range.label, true, openFilters)
            v.kinds.forEach { SwChip(it, it in f.kinds, { vm.toggleKind(it) }) }
        }
    }
}

@Composable
private fun Summary(v: HistoryView, currency: Currency, vm: HistoryViewModel) {
    val c = SpendWiseTheme.colors
    val tab = v.filters.tab
    Column(Modifier.padding(top = Spacing.section, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric(plural(v.count, tab.one, tab.many), currency.symbol, formatNumber(countUp(v.total), currency), positive = tab == HistoryTab.INCOME, icon = if (tab == HistoryTab.INCOME) SwIcons.MoneyIn else SwIcons.Receipt)
        if (v.split.isNotEmpty()) {
            Text(
                v.split.joinToString(" · ") { (k, amount) -> "$k ${formatAmount(amount, currency)}" },
                style = SwType.label, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
        }
        if (v.filtered) Box(Modifier.padding(horizontal = 8.dp)) { SwButton("Clear filters", vm::clearFilters, style = ButtonStyle.Text, compact = true) }
    }
}

@Composable
private fun HistoryRow(row: HistoryItem.Row, currency: Currency, divider: Boolean, onEdit: () -> Unit) {
    val c = SpendWiseTheme.colors
    val e = row.entry
    val income = e is Entry.Earned
    val meta = ((if (row.showDate) listOf(formatDate(e.date)) else emptyList()) + e.meta()).joinToString(" · ")
    // An entry waiting to sync has no server record yet, so it can't be edited (spec §1.6)
    ListRow(
        e.title,
        meta = listOf(meta, e.notes).filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null }, metaLines = 3,
        // Income reads "+₹…" in green; the sign says it as well as the colour
        amount = (if (income) "+" else "") + formatAmount(e.amount, currency), amountColor = if (income) c.positive else Color.Unspecified,
        icon = if (income) SwIcons.MoneyIn else SwIcons.forCategory(e.kind),
        divider = divider, onClick = if (e.pending) null else onEdit,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(v: HistoryView, vm: HistoryViewModel, onDismiss: () -> Unit) {
    val f = v.filters
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    SwSheet("Filters", onDismiss) {
        FieldLabel("Dates")
        FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryRange.entries.forEach { SwChip(it.label, f.range == it, { vm.selectRange(it) }) }
        }
        if (f.range == HistoryRange.CUSTOM || f.range == HistoryRange.ALL) {
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("From", if (f.customFrom.isEmpty()) "Start" else formatDate(f.customFrom), Modifier.weight(1f)) { picking = "from" }
                DateField("To", if (f.customTo.isEmpty()) "Today" else formatDate(f.customTo), Modifier.weight(1f)) { picking = "to" }
            }
        }
        FieldLabel("Sort")
        Box(gutter) { SegmentedControl(HistorySort.entries.map { it.label.substringBefore(' ') }, f.sort.ordinal, { vm.selectSort(HistorySort.entries[it]) }) }
        if (v.kinds.isNotEmpty()) {
            FieldLabel("Categories")
            FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                v.kinds.forEach { SwChip(it, it in f.kinds, { vm.toggleKind(it) }) }
            }
        }
        if (v.tags.isNotEmpty()) {
            FieldLabel("Tags")
            FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                v.tags.forEach { SwChip("#$it", it.lowercase() == v.searchedTag, { vm.toggleTag(it) }) }
            }
        }
        Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SwButton("Reset", vm::clearFilters, style = ButtonStyle.Secondary, enabled = v.filtered || f.sort != HistorySort.NEWEST)
            SwButton("Show ${plural(v.count, "result", "results")}", onDismiss, Modifier.weight(1f))
        }
    }
    when (picking) {
        "from" -> PickDateDialog(f.customFrom, vm::setCustomFrom, onDismiss = { picking = null })
        "to" -> PickDateDialog(f.customTo, vm::setCustomTo, onDismiss = { picking = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportSheet(vm: HistoryViewModel, currency: Currency) {
    val summary by vm.export.collectAsStateWithLifecycle()
    val s = summary ?: return
    val d = s.dialog
    val state by vm.state.collectAsStateWithLifecycle()
    val tab = (state as? HistoryState.Ready)?.view?.filters?.tab ?: HistoryTab.EXPENSES
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val empty = s.shownCount == 0 && s.bothCount == 0
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)

    SwSheet("Export", vm::closeExport) {
        Text("Pick the dates and what goes in the file.", style = SwType.label, color = c.textMuted, modifier = gutter)
        FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryRange.entries.forEach { r -> SwChip(r.label, d.range == r, { vm.setExport { it.copy(range = r) } }) }
        }
        if (d.range == HistoryRange.CUSTOM) {
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("From", formatDate(d.customFrom), Modifier.weight(1f)) { picking = "from" }
                DateField("To", formatDate(d.customTo), Modifier.weight(1f)) { picking = "to" }
            }
        }
        Box(gutter) {
            SegmentedControl(listOf("What's shown", "Expenses and income"), d.what.ordinal, { vm.setExport { e -> e.copy(what = ExportWhat.entries[it]) } })
        }
        val detail = if (d.what == ExportWhat.SHOWN) {
            val title = if (s.filters.isNotEmpty()) "What's shown · ${plural(s.shownCount, tab.one, tab.many)}" else "All ${tab.many} · ${s.shownCount}"
            title + " · " + (listOf(formatAmount(s.shownTotal, currency)) + s.filters).joinToString(" · ")
        } else "${plural(s.bothCount, "entry", "entries")}, without search or chips"
        Text(detail, style = SwType.label, color = c.textMuted, modifier = gutter)
        if (empty) Text("Nothing in these dates. Pick another time frame.", style = SwType.label, color = c.negative, modifier = gutter)
        SettingsGroup(null) {
            SettingsRow("Add totals at the end", trailing = Trailing.Switch(d.total) { on -> vm.setExport { it.copy(total = on) } })
        }
        Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SwButton("Save to device", vm::saveToDevice, Modifier.weight(1f), style = ButtonStyle.Secondary, enabled = !empty)
            SwButton("Share CSV", vm::runExport, Modifier.weight(1f), enabled = !empty)
        }
        Text("A CSV file, for Excel, Numbers or Google Sheets.", style = SwType.caption, color = c.textFaint, modifier = gutter)
    }
    when (picking) {
        "from" -> PickDateDialog(d.customFrom, { date -> vm.setExport { it.copy(customFrom = date) } }, onDismiss = { picking = null })
        "to" -> PickDateDialog(d.customTo, { date -> vm.setExport { it.copy(customTo = date) } }, onDismiss = { picking = null })
    }
}

/** Asks where to save the CSV (the system file picker, so no storage permission) once an export is built. */
@Composable
private fun SaveWhenReady(vm: HistoryViewModel) {
    val pending by vm.save.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), vm::saved)
    LaunchedEffect(pending) { pending?.let { picker.launch(it.name) } }
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

/** Swipe right to edit, left to delete; the row springs back either way, and delete asks first. */
@Composable
private fun SwipeRow(enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = SpendWiseTheme.colors
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state,
        backgroundContent = {
            val direction = state.dismissDirection
            if (direction == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            val toEdit = direction == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(c.surface).padding(horizontal = Spacing.gutter),
                horizontalArrangement = if (toEdit) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (toEdit) c.text else c.negative
                Icon(if (toEdit) SwIcons.Pencil else SwIcons.Trash, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (toEdit) "Edit" else "Delete", style = SwType.bodyStrong, color = color)
            }
        },
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) onEdit() else onDelete()
            scope.launch { state.reset() }
        },
    ) {
        Box(Modifier.background(c.bg)) { content() }
    }
}
