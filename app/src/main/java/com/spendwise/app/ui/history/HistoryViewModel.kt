package com.spendwise.app.ui.history

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.export.ExportFiles
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Entry
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.HistoryRange
import com.spendwise.app.domain.HistorySort
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.buildCsv
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.exportBoth
import com.spendwise.app.domain.exportFileName
import com.spendwise.app.domain.exportRange
import com.spendwise.app.domain.filterHistory
import com.spendwise.app.domain.historyDay
import com.spendwise.app.domain.historyRange
import com.spendwise.app.domain.kindsInUse
import com.spendwise.app.domain.searchTag
import com.spendwise.app.domain.tagsInUse
import com.spendwise.app.domain.totalsBy
import com.spendwise.app.ui.navigation.History
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

const val HISTORY_STEP = 50

enum class HistoryTab(val label: String, val one: String, val many: String) {
    EXPENSES("Expenses", "expense", "expenses"),
    INCOME("Income", "income entry", "income entries"),
}

/** Everything the filters hold except the search text, which is debounced separately like the web's 150 ms. */
data class HistoryFilters(
    val tab: HistoryTab = HistoryTab.EXPENSES,
    val range: HistoryRange = HistoryRange.ALL,
    /** The Custom date inputs, which start at this month (initHistory). */
    val customFrom: String = LocalDate.now().withDayOfMonth(1).toString(),
    val customTo: String = LocalDate.now().toString(),
    val sort: HistorySort = HistorySort.NEWEST,
    val kinds: Set<String> = emptySet(),
    val shown: Int = HISTORY_STEP,
)

private val isDate = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** The filters a History link asks for: any valid date picks Custom dates, an invalid one is left blank (initHistory). */
fun linkFilters(route: History): HistoryFilters {
    val base = HistoryFilters(tab = if (route.income) HistoryTab.INCOME else HistoryTab.EXPENSES, kinds = route.cats.toSet())
    val from = route.from?.takeIf { isDate.matches(it) }
    val to = route.to?.takeIf { isDate.matches(it) }
    return if (from == null && to == null) base else base.copy(range = HistoryRange.CUSTOM, customFrom = from.orEmpty(), customTo = to.orEmpty())
}

sealed interface HistoryItem {
    data class Day(val date: String, val label: String, val total: Double) : HistoryItem
    /** [showDate]: sorted by amount, so the row carries its date instead of a day heading. */
    data class Row(val entry: Entry, val showDate: Boolean) : HistoryItem
}

data class HistoryView(
    val filters: HistoryFilters,
    /** Every entry in the tab, before filters. 0 means the tab is empty, not filtered empty. */
    val allCount: Int,
    val kinds: List<String>,
    val tags: List<String>,
    /** The tag the search names, lowercase, for the pressed tag chip. */
    val searchedTag: String?,
    val count: Int,
    val total: Double,
    /** Filters narrow the list, so "Clear filters" shows. */
    val filtered: Boolean,
    /** For a tag search: where its money went. */
    val split: List<Pair<String, Double>>,
    val items: List<HistoryItem>,
    val left: Int,
)

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Failed(val message: String) : HistoryState
    data class Ready(val view: HistoryView) : HistoryState
}

enum class ExportWhat { SHOWN, BOTH }

data class ExportDialog(
    val range: HistoryRange,
    val customFrom: String,
    val customTo: String,
    val what: ExportWhat = ExportWhat.SHOWN,
    val total: Boolean = true,
)

/** What the export dialog shows for its current dates (dashboard.js › updateExportDialog). */
data class ExportSummary(
    val dialog: ExportDialog,
    val shownCount: Int,
    val shownTotal: Double,
    /** The chips and search applied: "Food & Dining", "“goa”". */
    val filters: List<String>,
    val bothCount: Int,
)

data class SharedFile(val uri: Uri, val name: String)

private data class Data(val expenses: List<Expense>, val income: List<Income>)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    expenseRepo: ExpenseRepository,
    incomeRepo: IncomeRepository,
    prefs: DevicePrefs,
    private val files: ExportFiles,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val route = savedStateHandle.toRoute<History>()
    private val filters = MutableStateFlow(linkFilters(route))

    private val _query = MutableStateFlow(route.q.orEmpty())
    /** The search box's text, as typed. */
    val query: StateFlow<String> = _query.asStateFlow()

    // One live source for the list and the export dialog. null while loading; only an expenses failure
    // is shown, the web just logs an income load error.
    private val data: StateFlow<Result<Data>?> = reload.flatMapLatest {
        combine(expenseRepo.expenses(), incomeRepo.income().catch { emit(emptyList()) }) { e, i -> Result.success(Data(e, i)) }
            .catch { emit(Result.failure(it)) }
            .onStart<Result<Data>?> { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<HistoryState> = combine(data, filters, _query.debounce(150)) { r, f, q ->
        when {
            r == null -> HistoryState.Loading
            r.isFailure -> HistoryState.Failed("Couldn't load your history. Check your connection and try again.")
            else -> HistoryState.Ready(view(r.getOrThrow(), f, q))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState.Loading)

    val currency: StateFlow<Currency> = prefs.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    private val _export = MutableStateFlow<ExportDialog?>(null)
    val export: StateFlow<ExportSummary?> = combine(_export, data, filters, _query) { dialog, d, f, q ->
        val loaded = d?.getOrNull()
        if (dialog == null || loaded == null) null else exportSummary(dialog, loaded, f, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A file ready for the share sheet; the screen clears it once shown. */
    private val _share = MutableStateFlow<SharedFile?>(null)
    val share: StateFlow<SharedFile?> = _share.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun retry() = reload.update { it + 1 }

    // Any filter change starts the list over at 50 rows (initHistory › rerender)
    private fun refilter(change: (HistoryFilters) -> HistoryFilters) = filters.update { change(it).copy(shown = HISTORY_STEP) }

    /** Categories and sources differ, so switching tabs clears the chips. */
    fun selectTab(tab: HistoryTab) = refilter { if (it.tab == tab) it else it.copy(tab = tab, kinds = emptySet()) }
    fun setQuery(q: String) {
        _query.value = q
        filters.update { it.copy(shown = HISTORY_STEP) }
    }
    fun selectRange(range: HistoryRange) = refilter { it.copy(range = range) }
    fun setCustomFrom(date: String) = refilter { it.copy(customFrom = date) }
    fun setCustomTo(date: String) = refilter { it.copy(customTo = date) }
    fun selectSort(sort: HistorySort) = refilter { it.copy(sort = sort) }
    fun toggleKind(kind: String) = refilter { it.copy(kinds = if (kind in it.kinds) it.kinds - kind else it.kinds + kind) }
    /** A tag chip searches "#tag"; tapping the pressed one clears the search. */
    fun toggleTag(tag: String) = setQuery(if (searchTag(_query.value) == tag.lowercase()) "" else "#$tag")
    fun showMore() = filters.update { it.copy(shown = it.shown + HISTORY_STEP) }

    /** clearHistoryFilters: search, dates and chips; the sort and tab stay. */
    fun clearFilters() {
        _query.value = ""
        refilter { it.copy(range = HistoryRange.ALL, kinds = emptySet()) }
    }

    /** The dialog's dates start as History's own. */
    fun openExport() {
        val f = filters.value
        _export.value = ExportDialog(f.range, f.customFrom, f.customTo)
    }
    fun closeExport() = _export.update { null }
    fun setExport(change: (ExportDialog) -> ExportDialog) = _export.update { it?.let(change) }

    fun runExport() {
        val summary = export.value ?: return
        val d = data.value?.getOrNull() ?: return
        val dialog = summary.dialog
        val what = when {
            dialog.what == ExportWhat.SHOWN && summary.shownCount > 0 -> ExportWhat.SHOWN
            summary.bothCount > 0 -> ExportWhat.BOTH
            else -> return
        }
        val f = filters.value
        val today = LocalDate.now()
        val (from, to) = exportRange(dialog.range, today, dialog.customFrom, dialog.customTo)
        val entries = if (what == ExportWhat.BOTH) exportBoth(d.expenses, d.income, from, to) else shownRows(d, f, _query.value, from, to)
        val name = exportFileName(
            if (what == ExportWhat.BOTH) "Expenses-and-income" else if (f.tab == HistoryTab.INCOME) "Income" else "Expenses",
            from, to, today.toString(),
        )
        val csv = buildCsv(entries, currency.value.code, dialog.total)
        _export.value = null
        viewModelScope.launch {
            try {
                _share.value = SharedFile(files.write(name, csv), name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Couldn't create the export file. Try again."
            }
        }
    }

    fun shareShown() = _share.update { null }
    fun messageShown() = _message.update { null }

    private fun tabRows(d: Data, tab: HistoryTab): List<Entry> =
        if (tab == HistoryTab.INCOME) d.income.map { Entry.Earned(it) } else d.expenses.map { Entry.Spent(it) }

    private fun shownRows(d: Data, f: HistoryFilters, q: String, from: String, to: String): List<Entry> {
        val all = tabRows(d, f.tab)
        val present = all.mapTo(HashSet()) { it.kind }
        return filterHistory(all, from, to, f.kinds.filterTo(HashSet()) { it in present }, q, f.sort)
    }

    /** renderHistory, as data. */
    private fun view(d: Data, f: HistoryFilters, q: String): HistoryView {
        val today = LocalDate.now()
        val all = tabRows(d, f.tab)
        val kinds = kindsInUse(all)
        // A chip whose kind has gone (its last entry deleted) no longer filters
        val selected = f.kinds.filterTo(LinkedHashSet()) { it in kinds }
        val (from, to) = historyRange(f.range, today, f.customFrom, f.customTo)
        val tag = if (f.tab == HistoryTab.EXPENSES) searchTag(q) else null
        // Tags used in the chosen dates, plus the one searched for
        val tags = if (f.tab == HistoryTab.EXPENSES) {
            tagsInUse(all.filter { r -> ((from.isEmpty() || r.date >= from) && (to.isEmpty() || r.date <= to)) || r.tags.any { it.lowercase() == tag } })
        } else emptyList()
        val rows = filterHistory(all, from, to, selected, q, f.sort)

        val byDate = f.sort != HistorySort.LARGEST
        val dayTotals = if (byDate) rows.groupingBy { it.date }.fold(0.0) { s, r -> s + r.amount } else emptyMap()
        val items = mutableListOf<HistoryItem>()
        var lastDay: String? = null
        rows.take(f.shown).forEach { r ->
            if (byDate && r.date != lastDay) {
                items += HistoryItem.Day(r.date, historyDay(r.date, today), dayTotals.getValue(r.date))
                lastDay = r.date
            }
            items += HistoryItem.Row(r, showDate = !byDate)
        }

        return HistoryView(
            filters = f.copy(kinds = selected),
            allCount = all.size,
            kinds = if (kinds.size < 2) emptyList() else kinds,
            tags = tags,
            searchedTag = tag,
            count = rows.size,
            total = rows.sumOf { it.amount },
            filtered = rows.size != all.size,
            split = if (tag != null && rows.isNotEmpty()) totalsBy(rows) else emptyList(),
            items = items,
            left = rows.size - f.shown,
        )
    }

    private fun exportSummary(dialog: ExportDialog, d: Data, f: HistoryFilters, q: String): ExportSummary {
        val (from, to) = exportRange(dialog.range, LocalDate.now(), dialog.customFrom, dialog.customTo)
        val shown = shownRows(d, f, q, from, to)
        val both = exportBoth(d.expenses, d.income, from, to)
        // An option with nothing in it is switched to the other, as the web's radios do
        val what = when {
            dialog.what == ExportWhat.SHOWN && shown.isEmpty() && both.isNotEmpty() -> ExportWhat.BOTH
            dialog.what == ExportWhat.BOTH && both.isEmpty() && shown.isNotEmpty() -> ExportWhat.SHOWN
            else -> dialog.what
        }
        return ExportSummary(
            dialog = dialog.copy(what = what),
            shownCount = shown.size,
            shownTotal = shown.sumOf { it.amount },
            filters = f.kinds.toList() + listOfNotNull(q.trim().takeIf { it.isNotEmpty() }?.let { "“$it”" }),
            bothCount = both.size,
        )
    }
}
