package com.spendwise.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.BudgetLevel
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.EmiPlan
import com.spendwise.app.domain.emiHeader
import com.spendwise.app.domain.meta
import com.spendwise.app.domain.percent
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.HomeFigures
import com.spendwise.app.domain.Meter
import com.spendwise.app.domain.TimeTab
import com.spendwise.app.domain.dateRange
import com.spendwise.app.domain.toFixed0
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.BudgetBar
import com.spendwise.app.ui.components.DayHeader
import com.spendwise.app.ui.components.ListRow
import com.spendwise.app.ui.components.MenuChip
import com.spendwise.app.ui.components.Metric
import com.spendwise.app.ui.components.SectionHeader
import com.spendwise.app.ui.components.SegmentedControl
import com.spendwise.app.ui.components.Stat
import com.spendwise.app.ui.components.StatGrid
import com.spendwise.app.ui.components.StatusPill
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.Toast
import com.spendwise.app.ui.components.WeekBars
import com.spendwise.app.ui.components.show
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.format.formatNumber
import com.spendwise.app.ui.format.plural
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.LocalDate

/**
 * Dashboard (spec §1.2): this month at a glance, then spending over time. Copy matches dashboard.js › renderHome.
 * Order: [topBar] → hero → sections, in one list.
 */
@Composable
fun DashboardScreen(
    topBar: @Composable () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onOpenIncome: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenInvest: () -> Unit,
    /** The period shown, as dates; null for both when History should show everything. */
    onOpenHistory: (from: String?, to: String?) -> Unit,
    /** Scroll to the goals section once loaded, then call [onGoalsShown]. */
    showGoals: Boolean = false,
    onGoalsShown: () -> Unit = {},
    dashboard: DashboardViewModel = hiltViewModel(),
    plans: PlansViewModel = hiltViewModel(),
) {
    val state by dashboard.state.collectAsStateWithLifecycle()
    val currency by dashboard.currency.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val notice by plans.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        plans.noticeShown()
        val result = snackbar.show(Toast(n.text, n.error), actionLabel = n.undoId?.let { "Undo" }, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) n.undoId?.let(plans::undoDue)
    }
    PlanDialogs(plans, currency)
    val list = rememberLazyListState()
    LaunchedEffect(showGoals, state) {
        if (showGoals && state is DashboardState.Ready) {
            withFrameNanos { } // the list is measured by the next frame
            list.scrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) // Goals is the last item
            onGoalsShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0), containerColor = SpendWiseTheme.colors.bg) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = list, contentPadding = PaddingValues(bottom = Spacing.section)) {
            item { topBar() }
            when (val s = state) {
                DashboardState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SpendWiseTheme.colors.text)
                    }
                }
                is DashboardState.Failed -> item {
                    Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(s.message, style = SwType.body, color = SpendWiseTheme.colors.negative)
                        SwButton("Try again", dashboard::retry, style = ButtonStyle.Secondary)
                    }
                }
                is DashboardState.Ready -> {
                    val money = { n: Double -> formatAmount(n, currency) }
                    item { Hero(s.home, currency, plans::openBudget) }
                    item { Section(0.dp) { Tiles(s.home, money, onOpenIncome, onOpenDebts) } }
                    if (s.home.due.isNotEmpty()) item { Section { DueThisMonth(s.home.due, money, plans) } }
                    if (s.emis.isNotEmpty()) item { Section { Emis(s.emis, currency) } }
                    item { Section { Today(s.home, s.firstTime, currency, money, onAddExpense, onEditExpense) } }
                    item { Section { Categories(s.home, s.firstTime, money) } }
                    item { Section { Trend(s.overTime, currency, dashboard) } }
                    item { Section { Recent(s.overTime, currency, onOpenHistory, onEditExpense) } }
                    item { Section { Score(s.home) } }
                    item { Section { Goals(s.home, money, plans, onOpenInvest) } }
                }
            }
        }
    }
}

@Composable
private fun Section(top: androidx.compose.ui.unit.Dp = Spacing.section, content: @Composable () -> Unit) =
    Box(Modifier.padding(top = top)) { content() }

@Composable
private fun Caption(text: String, color: Color = Color.Unspecified, modifier: Modifier = Modifier) =
    Text(text, style = SwType.label, color = if (color == Color.Unspecified) SpendWiseTheme.colors.textMuted else color, modifier = modifier.padding(horizontal = Spacing.gutter))

@Composable
private fun Hero(h: HomeFigures, currency: Currency, onBudget: () -> Unit) {
    val c = SpendWiseTheme.colors
    val money = { n: Double -> formatAmount(n, currency) }
    val today = LocalDate.now()
    val pace = today.dayOfMonth.toFloat() / today.lengthOfMonth()
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Counts up from 0 on first show, then from the old value on a change; the exact figure is shown once it lands.
        val count = remember { Animatable(0f) }
        LaunchedEffect(h.spent) { count.animateTo(h.spent.toFloat(), tween(800, easing = FastOutSlowInEasing)) }
        val shown = if (count.value == h.spent.toFloat()) h.spent else count.value.toDouble()
        Metric("Spent in ${h.monthName}", currency.symbol, formatNumber(shown, currency))
        when (val m = h.meter) {
            is Meter.Budget -> {
                val over = m.state.level == BudgetLevel.OVER
                val text = if (over) "${money(-m.state.left)} over your ${money(m.total)} budget"
                else "${Math.round(m.state.ratio * 100)}% of your ${money(m.total)} budget · ${money(m.state.left)} left"
                Box(Modifier.padding(horizontal = Spacing.gutter)) { BudgetBar(m.state.ratio.toFloat(), pace = pace, over = m.state.level != BudgetLevel.OK) }
                Row(Modifier.padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        over -> StatusPill("Over budget", color = c.negative)
                        m.state.ratio > pace -> StatusPill("Ahead of pace")
                        else -> StatusPill("On track")
                    }
                    Text(text, style = SwType.label, color = if (over) c.negative else c.textMuted, modifier = Modifier.weight(1f))
                }
            }
            is Meter.Income -> {
                val over = m.ratio > 1
                val text = if (over) "${money(m.spent - m.income)} more than your ${money(m.income)} income"
                else "${Math.round(m.ratio * 100)}% of your ${money(m.income)} income · ${money(m.income - m.spent)} left"
                Box(Modifier.padding(horizontal = Spacing.gutter)) { BudgetBar(m.ratio.toFloat(), pace = pace) }
                Caption(text, if (over) c.negative else Color.Unspecified)
            }
            null -> Caption("Add this month's income to see how much is left.")
        }
        if (h.perDay != null && h.forecastTotal != null) {
            Caption("${money(h.perDay)} a day · on track for ${money(h.forecastTotal)} by the end of ${h.monthName}")
        }
        Box(Modifier.padding(horizontal = 8.dp)) {
            SwButton(if (h.budget.total != null) "Update budget" else "Set a monthly budget", onBudget, style = ButtonStyle.Text, compact = true)
        }
    }
}

@Composable
private fun Tiles(h: HomeFigures, money: (Double) -> String, onOpenIncome: () -> Unit, onOpenDebts: () -> Unit) {
    val c = SpendWiseTheme.colors
    val savedSub = buildString {
        append(
            when {
                h.saved == null -> "Needs income entries"
                h.saved < 0 -> "Overspent this month"
                else -> "${Math.round(h.saved / h.income * 100)}% of income"
            },
        )
        if (h.invested > 0) append(" · ${money(h.invested)} invested")
    }
    StatGrid(
        listOf(
            Stat(
                "Income", if (h.income > 0) money(h.income) else "—", SwIcons.MoneyIn, if (h.income > 0) c.positive else Color.Unspecified,
                if (h.incomeCount > 0) "${plural(h.incomeCount, "entry", "entries")} this month" else "None this month", onOpenIncome,
            ),
            Stat("Saved", h.saved?.let { if (it < 0) "−${money(-it)}" else money(it) } ?: "—", SwIcons.Wallet, if ((h.saved ?: 0.0) < 0) c.negative else Color.Unspecified, savedSub),
            Stat("You owe", money(h.owe), SwIcons.MoneyOut, if (h.owe > 0) c.negative else Color.Unspecified, onClick = onOpenDebts),
            Stat("Owed to you", money(h.owed), SwIcons.People, if (h.owed > 0) c.positive else Color.Unspecified, onClick = onOpenDebts),
        ),
    )
}

@Composable
private fun ExpenseRow(e: Expense, money: (Double) -> String, divider: Boolean, onEdit: (String) -> Unit) {
    val named = if (e.description.isNotEmpty() && e.description != "-") e.description else ""
    // The category is left out of the subtitle when it is already the title
    val meta = listOf(
        if (named.isNotEmpty() && !named.equals(e.category, ignoreCase = true)) e.category else "",
        e.payment,
        if (e.pending) "waiting to sync" else "",
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    ListRow(named.ifEmpty { e.category }, meta = meta.ifEmpty { null }, amount = money(e.amount), icon = SwIcons.forCategory(e.category), divider = divider,
        // An entry waiting to sync has no server record yet, so it can't be edited
        onClick = if (e.pending) null else ({ onEdit(e.id) }),
    )
}

@Composable
private fun Today(h: HomeFigures, firstTime: Boolean, currency: Currency, money: (Double) -> String, onAddExpense: () -> Unit, onEdit: (String) -> Unit) {
    val c = SpendWiseTheme.colors
    Column {
        SectionHeader("Today", action = if (h.todays.isNotEmpty()) money(h.todaySpent) else null)
        if (h.todays.isNotEmpty()) Caption(plural(h.todays.size, "entry", "entries"))
        val cmp = h.todayCompare
        if (cmp != null) {
            val text = if (cmp.gap < 1) "Right on your daily average of ${money(h.average)}"
            else "${money(cmp.gap)} ${if (cmp.over) "more" else "less"} than your daily average of ${money(h.average)}"
            Box(Modifier.padding(horizontal = Spacing.gutter, vertical = 12.dp)) { BudgetBar(cmp.fill.toFloat(), pace = cmp.averageAt.toFloat(), over = cmp.over) }
            Caption(text, if (cmp.over) c.negative else Color.Unspecified)
        } else if (h.todays.isNotEmpty()) {
            Caption("Your daily average shows here from tomorrow")
        }
        if (h.todays.isEmpty()) {
            Caption(if (firstTime) "Start tracking. It takes a few seconds." else "Nothing added today yet.")
            Box(Modifier.padding(horizontal = 8.dp)) {
                SwButton(if (firstTime) "Add your first expense" else "Add an expense", onAddExpense, style = ButtonStyle.Text, compact = true)
            }
        } else {
            h.todays.take(5).forEachIndexed { i, e -> ExpenseRow(e, money, divider = i > 0, onEdit) }
        }
    }
}

/** EMIs: each plan's payments so far, what's left and when it ends (dashboard.js › renderEmis). */
@Composable
private fun Emis(plans: List<EmiPlan>, currency: Currency) {
    val whole = { n: Double -> formatAmount(n, currency) }
    val today = LocalDate.now()
    Column {
        SectionHeader("EMIs")
        Caption(emiHeader(plans, whole))
        plans.forEachIndexed { i, p ->
            Column {
                ListRow(p.title, meta = p.meta(today, whole), amount = "${whole(p.monthly)}/mo", icon = SwIcons.Receipt, divider = i > 0)
                Box(Modifier.padding(start = Spacing.gutter + 48.dp, end = Spacing.gutter, bottom = 12.dp)) { BudgetBar(p.percent / 100f) }
            }
        }
    }
}

@Composable
private fun Categories(h: HomeFigures, firstTime: Boolean, money: (Double) -> String) {
    val c = SpendWiseTheme.colors
    Column {
        SectionHeader("Categories")
        if (h.categories.isEmpty()) {
            Caption(if (firstTime) "Your top categories show here once you add expenses." else "No expenses this month yet.")
            return@Column
        }
        val overCount = h.categories.count { it.cap?.level == BudgetLevel.OVER }
        if (overCount > 0) Caption("${plural(overCount, "category is", "categories are")} over its limit", c.negative)
        // Over-limit first; the sort is stable, so the rest keep their order by amount
        h.categories.sortedByDescending { it.cap?.level == BudgetLevel.OVER }.forEachIndexed { i, cat ->
            val cap = cat.cap
            val over = cap?.level == BudgetLevel.OVER
            val detail = if (cap != null && cat.limit != null) {
                if (over) "${money(-cap.left)} over the ${money(cat.limit)} limit"
                else "${money(cat.amount)} of ${money(cat.limit)} limit · ${money(cap.left)} left"
            } else "${toFixed0(cat.share)}% of this month's spending"
            Column {
                ListRow(cat.category, meta = detail, metaColor = if (over) c.negative else Color.Unspecified, amount = money(cat.amount), icon = SwIcons.forCategory(cat.category), divider = i > 0)
                Box(Modifier.padding(start = Spacing.gutter + 48.dp, end = Spacing.gutter, bottom = 12.dp)) {
                    BudgetBar(cat.fill.toFloat(), over = cap != null && cap.level != BudgetLevel.OK)
                }
            }
        }
    }
}

private val MONTH_NAMES = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

private val TABS = listOf(TimeTab.DAILY to "Day", TimeTab.WEEKLY to "Week", TimeTab.MONTHLY to "Month", TimeTab.YEARLY to "Year")

@Composable
private fun Trend(o: OverTime, currency: Currency, vm: DashboardViewModel) {
    val c = SpendWiseTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Spending trend")
        Box(Modifier.padding(horizontal = Spacing.gutter)) {
            SegmentedControl(TABS.map { it.second }, TABS.indexOfFirst { it.first == o.view.tab }, { vm.selectTab(TABS[it].first) })
        }
        Row(Modifier.padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuChip(
                o.view.period.month?.let { MONTH_NAMES[it.toInt() - 1] } ?: "All Months",
                listOf(null to "All Months") + MONTH_NAMES.mapIndexed { i, n -> (i + 1).toString().padStart(2, '0') to n },
                vm::selectMonth,
            )
            MenuChip(o.view.period.year ?: "All Years", listOf(null to "All Years") + o.years.map { it to it }, vm::selectYear)
        }
        if (o.trend.values.isEmpty()) {
            Caption("No spending to chart yet.")
        } else {
            val current = when (o.view.tab) {
                TimeTab.MONTHLY -> (o.view.period.month?.toInt() ?: LocalDate.now().monthValue) - 1
                else -> o.trend.values.lastIndex
            }
            Box(Modifier.padding(horizontal = Spacing.gutter)) { WeekBars(o.trend.values.map { it.toFloat() }, o.trend.labels, current) }
        }
        val s = o.summary
        Caption(
            "${formatAmount(s.total, currency)} in ${plural(s.count, "entry", "entries")} · ${formatAmount(s.averagePerDay, currency)} a day · largest ${formatAmount(s.largest, currency)}",
            c.textMuted,
        )
    }
}

@Composable
private fun Recent(o: OverTime, currency: Currency, onOpenHistory: (String?, String?) -> Unit, onEdit: (String) -> Unit) {
    val money = { n: Double -> formatAmount(n, currency) }
    val range = o.view.period.dateRange()
    Column {
        // The rest are in History, which can search and filter them
        SectionHeader("Recent", action = if (o.rows.size > LATEST_ROWS) "Show more" else null, onAction = { onOpenHistory(range?.first, range?.second) })
        if (o.rows.isEmpty()) Caption("No expenses in this period.")
        o.rows.take(LATEST_ROWS).groupBy { it.date }.forEach { (date, rows) ->
            DayHeader(formatDate(date), money(rows.sumOf { it.amount }))
            rows.forEachIndexed { i, e -> ExpenseRow(e, money, divider = i > 0, onEdit) }
        }
    }
}

private const val LATEST_ROWS = 5

@Composable
private fun Score(h: HomeFigures) {
    val c = SpendWiseTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val s = h.score
    Column {
        SectionHeader("Money score", action = if (s != null) (if (open) "Hide" else "Details") else null, onAction = { open = !open })
        if (s == null) {
            Caption("No score yet. Add this month's income to see your score.")
            return@Column
        }
        Row(Modifier.padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${s.score}", style = SwType.title, color = if (s.score < 40) c.negative else c.text)
            Text(s.label, style = SwType.body, color = c.text, modifier = Modifier.padding(bottom = 4.dp))
        }
        Caption(s.tip)
        if (open) s.parts.forEach { part ->
            val pct = Math.round(part.value * 100)
            Row(Modifier.padding(horizontal = Spacing.gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(part.name, Modifier.width(96.dp), style = SwType.label, color = c.text)
                Box(Modifier.weight(1f)) { BudgetBar(maxOf(pct, 2) / 100f, over = part.value < 0.5) }
                Text("$pct%", Modifier.width(44.dp), style = SwType.label, color = c.textMuted)
            }
        }
    }
}
