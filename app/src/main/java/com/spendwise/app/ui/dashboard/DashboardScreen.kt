package com.spendwise.app.ui.dashboard

import com.spendwise.app.ui.components.IconBadge
import com.spendwise.app.ui.components.Panel
import com.spendwise.app.ui.components.categoryIcon
import com.spendwise.app.ui.components.Toast
import com.spendwise.app.ui.components.show
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import com.spendwise.app.R
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.dateRange
import com.spendwise.app.domain.greeting
import java.time.LocalTime
import com.spendwise.app.domain.BudgetLevel
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.HomeFigures
import com.spendwise.app.domain.Meter
import com.spendwise.app.domain.TimeTab
import com.spendwise.app.domain.Trend
import com.spendwise.app.domain.toFixed0
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.format.formatMoney
import com.spendwise.app.ui.format.formatWhole
import com.spendwise.app.ui.format.plural
import com.spendwise.app.ui.theme.SpendWiseTheme

/** Dashboard (spec §1.2): this month at a glance, then spending over time. Copy matches dashboard.js › renderHome. */
@Composable
fun DashboardScreen(
    onAddExpense: () -> Unit,
    onOpenIncome: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenInvest: () -> Unit,
    /** The period shown, as dates; null for both when History should show everything. */
    onOpenHistory: (from: String?, to: String?) -> Unit,
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val s = state) {
            DashboardState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is DashboardState.Failed -> item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = dashboard::retry) { Text("Try again") }
                }
            }
            is DashboardState.Ready -> {
                val money = { n: Double -> formatWhole(n, currency) }
                item { Panel { Hero(s.home, dashboard.name, money, plans::openBudget) } }
                item { Panel { Today(s.home, s.firstTime, currency, money, onAddExpense) } }
                item { Tiles(s.home, money, onOpenIncome, onOpenDebts) }
                if (s.home.due.isNotEmpty()) item { Panel { DueThisMonth(s.home.due, money, plans) } }
                item { Panel { Score(s.home) } }
                item { Panel { Goals(s.home, money, plans, onOpenInvest) } }
                item { Panel { WhereItWent(s.home, s.firstTime, money) } }
                overTime(s.overTime, currency, dashboard, onOpenHistory)
            }
        }
    }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun Muted(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

/** A flat bar: [fill] of the track, in [color], with an optional marker at [tick]. Both are fractions of the width. */
@Composable
private fun Bar(fill: Double, color: Color, label: String, modifier: Modifier = Modifier, tick: Double? = null) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = label }) {
        drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
        val w = (fill.coerceIn(0.0, 1.0) * size.width).toFloat()
        if (w > 0) drawLine(color, Offset(0f, size.height / 2), Offset(w, size.height / 2), size.height, StrokeCap.Round)
        tick?.let {
            val x = (it.coerceIn(0.0, 1.0) * size.width).toFloat()
            drawLine(tickColor, Offset(x, -4.dp.toPx()), Offset(x, size.height + 4.dp.toPx()), 2.dp.toPx())
        }
    }
}

@Composable
private fun Hero(h: HomeFigures, name: String, money: (Double) -> String, onBudget: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(greeting(LocalTime.now().hour, name))
        Muted("${h.monthName} so far")
        Text(money(h.spent), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        val colors = MaterialTheme.colorScheme
        when (val m = h.meter) {
            is Meter.Budget -> {
                val over = m.state.level == BudgetLevel.OVER
                val text = if (over) "${money(-m.state.left)} over your ${money(m.total)} budget"
                else "${Math.round(m.state.ratio * 100)}% of your ${money(m.total)} budget · ${money(m.state.left)} left"
                Bar(minOf(m.state.ratio, 1.0), if (m.state.level == BudgetLevel.OK) colors.primary else colors.error, text)
                Text(text, color = if (over) colors.error else colors.onSurface)
            }
            is Meter.Income -> {
                val over = m.ratio > 1
                val text = if (over) "${money(m.spent - m.income)} more than your ${money(m.income)} income"
                else "${Math.round(m.ratio * 100)}% of your ${money(m.income)} income · ${money(m.income - m.spent)} left"
                Bar(minOf(m.ratio, 1.0), if (over) colors.error else colors.primary, text)
                Text(text, color = if (over) colors.error else colors.onSurface)
            }
            null -> Muted("Add this month's income to see how much is left.")
        }
        if (h.perDay != null && h.forecastTotal != null) {
            Muted("${money(h.perDay)} a day · on track for ${money(h.forecastTotal)} by the end of ${h.monthName}")
        }
        TextButton(onClick = onBudget, contentPadding = PaddingValues(0.dp)) { Text(if (h.budget.total != null) "Update budget" else "Set a monthly budget") }
    }
}

@Composable
private fun Today(h: HomeFigures, firstTime: Boolean, currency: Currency, money: (Double) -> String, onAddExpense: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Today")
                if (h.todays.isNotEmpty()) Muted(plural(h.todays.size, "entry", "entries"))
            }
            if (h.todays.isNotEmpty()) Text(money(h.todaySpent), style = MaterialTheme.typography.titleLarge)
        }
        val c = h.todayCompare
        if (c != null) {
            val text = if (c.gap < 1) "Right on your daily average of ${money(h.average)}"
            else "${money(c.gap)} ${if (c.over) "more" else "less"} than your daily average of ${money(h.average)}"
            Bar(c.fill, if (c.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, text, tick = c.averageAt)
            Muted(text)
        } else if (h.todays.isNotEmpty()) {
            Muted("Your daily average shows here from tomorrow")
        }
        if (h.todays.isEmpty()) {
            val link = if (firstTime) "Add your first expense" else "Add an expense"
            if (!firstTime) Muted("Nothing added today yet.")
            TextButton(onClick = onAddExpense, contentPadding = PaddingValues(0.dp)) { Text(link) }
            if (firstTime) Muted("Start tracking. It takes a few seconds.")
        } else {
            h.todays.take(5).forEach { TodayRow(it, currency) }
        }
    }
}

@Composable
private fun TodayRow(e: Expense, currency: Currency) {
    val named = if (e.description.isNotEmpty() && e.description != "-") e.description else ""
    // The category is left out of the subtitle when it is already the title
    val meta = listOf(
        if (named.isNotEmpty() && !named.equals(e.category, ignoreCase = true)) e.category else "",
        e.payment,
        if (e.pending) "waiting to sync" else "",
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(categoryIcon(e.category))
        Column(Modifier.weight(1f)) {
            Text(named.ifEmpty { e.category }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Muted(meta)
        }
        Text(formatMoney(e.amount, currency))
    }
}

@Composable
private fun Tiles(h: HomeFigures, money: (Double) -> String, onOpenIncome: () -> Unit, onOpenDebts: () -> Unit) {
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
    val moneyIn = SpendWiseTheme.moneyColors.moneyIn
    val owedColor = MaterialTheme.colorScheme.error
    // Each pair matches the taller tile's height
    val pair = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(pair, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                "Income", R.drawable.ic_tile_income, if (h.income > 0) money(h.income) else "—",
                if (h.incomeCount > 0) "${plural(h.incomeCount, "entry", "entries")} this month" else "None this month",
                moneyIn, Modifier.weight(1f), onOpenIncome,
            )
            Tile(
                "Saved", R.drawable.ic_tile_saved, h.saved?.let { if (it < 0) "−${money(-it)}" else money(it) } ?: "—", savedSub,
                if ((h.saved ?: 0.0) < 0) owedColor else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f),
            )
        }
        Row(pair, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("You owe", R.drawable.ic_tile_owe, money(h.owe), null, if (h.owe > 0) owedColor else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), onOpenDebts)
            Tile("Owed to you", R.drawable.ic_tile_owed, money(h.owed), null, if (h.owed > 0) moneyIn else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), onOpenDebts)
        }
    }
}

@Composable
private fun Tile(label: String, icon: Int, value: String, sub: String?, color: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Panel(modifier.fillMaxHeight(), onClick = onClick, onClickLabel = onClick?.let { "Open $label" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Muted(label)
        }
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Score(h: HomeFigures) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_score), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            SectionTitle("Money score")
        }
        val s = h.score
        if (s == null) {
            Text("—", style = MaterialTheme.typography.headlineMedium)
            Text("No score yet")
            Muted("Add this month's income to see your score")
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${s.score}", style = MaterialTheme.typography.headlineMedium, color = if (s.score < 40) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(s.label, modifier = Modifier.padding(bottom = 4.dp))
        }
        Muted(s.tip)
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
            Text(if (open) "Hide what makes up your score" else "What makes up your score")
        }
        if (open) s.parts.forEach { part ->
            val pct = Math.round(part.value * 100)
            val weak = part.value < 0.5
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(part.name, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                Bar(maxOf(pct, 2) / 100.0, if (weak) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, "${part.name} $pct%", Modifier.weight(1f))
                Text("$pct%", Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WhereItWent(h: HomeFigures, firstTime: Boolean, money: (Double) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Where it went")
        if (h.categories.isEmpty()) {
            Muted(if (firstTime) "Your top categories show here once you add expenses." else "No expenses this month yet.")
        }
        h.categories.forEach { c ->
            val cap = c.cap
            val over = cap?.level == BudgetLevel.OVER
            val detail = if (cap != null && c.limit != null) {
                if (over) "${money(-cap.left)} over the ${money(c.limit)} limit"
                else "${money(c.amount)} of ${money(c.limit)} limit · ${money(cap.left)} left"
            } else "${toFixed0(c.share)}% of this month's spending"
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(categoryIcon(c.category)), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(c.category, Modifier.weight(1f).padding(start = 8.dp))
                    Text(money(c.amount))
                }
                val warn = cap != null && cap.level != BudgetLevel.OK
                Bar(c.fill, if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, "${c.category}: $detail")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val MONTH_NAMES = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

private fun LazyListScope.overTime(o: OverTime, currency: Currency, vm: DashboardViewModel, onOpenHistory: (String?, String?) -> Unit) {
    item {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Spending over time")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    o.view.period.month?.let { MONTH_NAMES[it.toInt() - 1] } ?: "All Months",
                    listOf(null to "All Months") + MONTH_NAMES.mapIndexed { i, n -> (i + 1).toString().padStart(2, '0') to n },
                    vm::selectMonth,
                )
                Picker(o.view.period.year ?: "All Years", listOf(null to "All Years") + o.years.map { it to it }, vm::selectYear)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Total", formatMoney(o.summary.total, currency), Modifier.weight(1f))
                Stat("Entries", "${o.summary.count}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Avg / day", formatMoney(o.summary.averagePerDay, currency), Modifier.weight(1f))
                Stat("Largest", formatMoney(o.summary.largest, currency), Modifier.weight(1f))
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TimeTab.entries.forEachIndexed { i, tab ->
                    SegmentedButton(
                        selected = o.view.tab == tab,
                        onClick = { vm.selectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(i, TimeTab.entries.size),
                        icon = {},
                    ) { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1) }
                }
            }
            LineChart(o.trend, currency)
        }
    }
    if (o.rows.isEmpty()) item { Muted("No expenses in this period.") }
    items(o.rows.take(LATEST_ROWS), key = { "row-" + it.id }) { e ->
        Column {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.description.ifEmpty { "-" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Muted("${formatDate(e.date)} · ${e.category}")
                }
                Text(formatMoney(e.amount, currency))
            }
            HorizontalDivider()
        }
    }
    // The rest are in History, which can search and filter them
    if (o.rows.size > LATEST_ROWS) item {
        val range = o.view.period.dateRange()
        OutlinedButton(onClick = { onOpenHistory(range?.first, range?.second) }, modifier = Modifier.fillMaxWidth()) {
            Text("Show more")
        }
    }
}

private const val LATEST_ROWS = 5

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Muted(label)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An outlined button showing [current] that opens a menu of [options] (value to label). */
@Composable
internal fun <T> Picker(current: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(current)
            Icon(painterResource(R.drawable.ic_back), contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp).rotate(if (open) 90f else -90f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

/** The trend as a flat line: accent stroke and points, a baseline, the top value and thinned x labels. No fills or shadows. */
@Composable
private fun LineChart(trend: Trend, currency: Currency) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val description = trend.labels.zip(trend.values) { l, v -> "$l ${formatWhole(v, currency)}" }.joinToString(", ")
    if (trend.values.isEmpty()) {
        Muted("No spending to chart yet.")
        return
    }
    val max = trend.values.max()
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "Spending chart: $description" }) {
        val labelH = 20.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = size.height - labelH
        val n = trend.values.size
        val step = if (n > 1) size.width / (n - 1) else 0f
        fun x(i: Int) = if (n > 1) i * step else size.width / 2
        fun y(v: Double) = if (max > 0) (bottom - (v / max) * (bottom - top)).toFloat() else bottom

        drawLine(axis, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        drawText(measurer, formatWhole(max, currency), Offset(0f, 0f), style = labelStyle)

        val path = Path()
        trend.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        trend.values.forEachIndexed { i, v -> drawCircle(line, 3.dp.toPx(), Offset(x(i), y(v))) }

        // Up to about 6 labels, so the 12 months and long week ranges don't collide
        val every = maxOf(1, (n + 5) / 6)
        trend.labels.forEachIndexed { i, label ->
            if (i % every != 0) return@forEachIndexed
            val text = measurer.measure(label, labelStyle)
            val lx = (x(i) - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(lx, bottom + 4.dp.toPx()))
        }
    }
}
