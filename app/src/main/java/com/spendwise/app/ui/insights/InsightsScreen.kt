package com.spendwise.app.ui.insights

import com.spendwise.app.ui.components.Panel
import com.spendwise.app.ui.components.Toast
import com.spendwise.app.ui.components.show
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.spendwise.app.ui.components.categoryIcon
import com.spendwise.app.ui.components.paymentIcon
import com.spendwise.app.ui.components.IconBadge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.BudgetReport
import com.spendwise.app.domain.Change
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Forecast
import com.spendwise.app.domain.InsightFigure
import com.spendwise.app.domain.InsightPeriod
import com.spendwise.app.domain.InsightSummary
import com.spendwise.app.domain.PieSlice
import com.spendwise.app.domain.catRows
import com.spendwise.app.domain.compareCard
import com.spendwise.app.domain.figure
import com.spendwise.app.domain.fixedCard
import com.spendwise.app.domain.forecastCard
import com.spendwise.app.domain.meta
import com.spendwise.app.domain.monthRows
import com.spendwise.app.domain.paymentRows
import com.spendwise.app.domain.shareText
import com.spendwise.app.domain.sub
import com.spendwise.app.domain.title
import com.spendwise.app.domain.toFixed0
import com.spendwise.app.domain.value
import com.spendwise.app.domain.weekdayCard
import com.spendwise.app.ui.dashboard.PlanDialogs
import com.spendwise.app.ui.dashboard.PlansViewModel
import com.spendwise.app.ui.expenses.PickDateDialog
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.domain.searchedText
import com.spendwise.app.domain.askRows
import com.spendwise.app.domain.askName
import com.spendwise.app.domain.askHistory
import com.spendwise.app.domain.answerFor
import com.spendwise.app.domain.Entry
import com.spendwise.app.domain.AskHistory
import com.spendwise.app.domain.ASK_MAX
import com.spendwise.app.domain.ASK_EXAMPLES
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.spendwise.app.ui.format.formatWhole
import com.spendwise.app.ui.theme.SpendWiseTheme

/** Insights (spec §1.7): Ask (read on the device), the forecast, the period tabs, then the period's cards in the web's order. No AI. */
@Composable
fun InsightsScreen(
    onAddExpense: () -> Unit,
    onOpenHistory: (AskHistory) -> Unit,
    insights: InsightsViewModel = hiltViewModel(),
    plans: PlansViewModel = hiltViewModel(),
    coach: CoachViewModel = hiltViewModel(),
) {
    val state by insights.state.collectAsStateWithLifecycle()
    val asking by insights.asking.collectAsStateWithLifecycle()
    val coachState by coach.state.collectAsStateWithLifecycle()
    val consentAsked by insights.consentAsked.collectAsStateWithLifecycle()
    if (consentAsked) ConsentDialog(insights::answerConsent)
    val currency by insights.currency.collectAsStateWithLifecycle()
    // insights-v2.js › money: whole, with a no-break space so the symbol never wraps from its number
    val money = { n: Double -> formatWhole(n, currency).replaceFirst(' ', ' ') }
    val snackbar = remember { SnackbarHostState() }
    val notice by plans.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        plans.noticeShown()
        snackbar.show(Toast(n.text, n.error))
    }
    PlanDialogs(plans, currency)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Insights", style = MaterialTheme.typography.headlineSmall) }
            when (val s = state) {
                InsightsState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp).semantics { contentDescription = "Adding up your numbers" }, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is InsightsState.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = insights::retry) { Text("Try again") }
                    }
                }
                is InsightsState.Ready -> {
                    item { Panel { AskSection(s.ask, asking, insights::ask, money, onOpenHistory) } }
                    item { ForecastSection(s.forecast, money) }
                    item { Box(Modifier.padding(top = 8.dp)) { Periods(s.choice, insights) } }
                    val g = s.glance
                    when {
                        s.periodError != null -> item { Text(s.periodError, color = MaterialTheme.colorScheme.error) }
                        g == null -> Unit
                        !g.summary.hasData -> item {
                            Column {
                                Muted("Nothing recorded in ${g.range.label}. Try a longer period, or add an expense.")
                                TextButton(onClick = onAddExpense, contentPadding = PaddingValues(0.dp)) { Text("Add an expense") }
                            }
                        }
                        else -> {
                            item { Panel { CompareSection(g.summary, g, money) } }
                            item { Panel { BudgetSection(g.budget, money, plans::openBudget) } }
                            if (g.pie.isNotEmpty()) item { Panel { PieSection(g.pie, g.summary.spent, money) } }
                            if (g.summary.movers.isNotEmpty()) item { Panel { ChangedSection(g.summary, money) } }
                            if (g.summary.biggest.isNotEmpty()) item { Panel { BiggestSection(g.summary, money) } }
                            weekdayCard(g.summary, money)?.let { w -> item { Panel { WeekdaySection(g.summary, w, money) } } }
                            paymentRows(g.summary, money).takeIf { it.isNotEmpty() }?.let { rows -> item { Panel { PaymentSection(rows) } } }
                            fixedCard(g.summary, money)?.let { f -> item { Panel { FixedSection(f) } } }
                        }
                    }
                    item { Panel { CoachSection(coachState, g.takeIf { s.periodError == null }, coach) } }
                }
            }
        }
    }
}

// ── Shared pieces ──

@Composable
private fun Title(text: String, aside: String? = null) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    aside?.let { Muted(it) }
}

@Composable
private fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun BigFigure(f: InsightFigure) = Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(f.amount, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    Text(f.unit, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
}

/** Up is spending going up: the "owed/over" colour; down is green. The words say which way too. */
@Composable
private fun ChangeLine(c: Change) =
    Text(c.text, style = MaterialTheme.typography.bodyMedium, color = if (c.up) MaterialTheme.colorScheme.error else SpendWiseTheme.moneyColors.moneyIn)

@Composable
private fun Row2(title: String, sub: String?, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, @DrawableRes icon: Int? = null) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let { IconBadge(it) }
        Column(Modifier.weight(1f).padding(start = if (icon != null) 12.dp else 0.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }

@Composable
private fun Dot(color: Color) = Box(Modifier.padding(top = 6.dp).size(10.dp).background(color, CircleShape))

@Composable
private fun Legend(color: Color, title: String, sub: String) = Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Dot(color)
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Flat bar segments of one track, each a fraction of the width, drawn in order. */
@Composable
private fun StackBar(parts: List<Pair<Double, Color>>, label: String, height: Int = 12) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(height.dp).clearAndSetSemantics { contentDescription = label }) {
        drawRect(track)
        var x = 0f
        parts.forEach { (fraction, color) ->
            val w = (fraction.coerceIn(0.0, 1.0) * size.width).toFloat().coerceAtMost(size.width - x)
            if (w > 0) drawRect(color, Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
            x += w
        }
    }
}

/** The accent mixed into the panel behind it, like the web's color-mix for the forecast's and split's shades. */
@Composable
private fun shade(amount: Float) = lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primary, amount)

// ── Forecast ──

@Composable
private fun ForecastSection(f: Forecast, money: (Double) -> String) {
    val card = forecastCard(f, money) ?: return
    val colors = listOf(shade(1f), shade(0.55f), shade(0.25f))
    Panel {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Forecast")
        BigFigure(card.figure)
        card.vsLastMonth?.let { ChangeLine(it) }
        StackBar(
            card.parts.zip(colors),
            "Spent so far ${money(f.spent)}, bills still due ${money(f.billsTotal)}, at your usual pace ${money(f.usualLeft)}",
        )
        card.legend.forEachIndexed { i, (title, sub) -> Legend(colors[i], title, sub) }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Muted(card.nextName)
            Text(card.nextTotal, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
        }
        Muted(card.nextNote)
    }
    }
}

// ── Period tabs ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Periods(choice: PeriodChoice, vm: InsightsViewModel) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightPeriod.entries.forEach { p ->
                FilterChip(selected = choice.period == p, onClick = { vm.select(p) }, label = { Text(p.label) })
            }
        }
        if (choice.period == InsightPeriod.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { picking = "start" }, label = { Text("From ${formatDate(choice.customStart)}") })
                AssistChip(onClick = { picking = "end" }, label = { Text("To ${formatDate(choice.customEnd)}") })
            }
        }
    }
    when (picking) {
        "start" -> PickDateDialog(choice.customStart, vm::setCustomStart) { picking = null }
        "end" -> PickDateDialog(choice.customEnd, vm::setCustomEnd) { picking = null }
    }
}

// ── Cards ──

@Composable
private fun CompareSection(s: InsightSummary, g: Glance, money: (Double) -> String) {
    val c = compareCard(s, g.range, money)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Title(c.title)
        c.none?.let { Muted(it) }
        c.figure?.let { BigFigure(it) }
        c.change?.let { ChangeLine(it) }
        c.rows.forEach { (k, v) -> Row2(k, null, v) }
    }
}

@Composable
private fun BudgetSection(rep: BudgetReport?, money: (Double) -> String, onBudget: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("Against your budget")
        if (rep == null) {
            Muted("See how each month goes against a plan.")
            TextButton(onClick = onBudget, contentPadding = PaddingValues(0.dp)) { Text("Set a monthly budget") }
            return@Column
        }
        rep.figure(money)?.let { BigFigure(it) }
        rep.monthRows(money).zip(rep.months).forEach { (row, m) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(row.label + if (row.current) " so far" else "", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    row.status?.let { Text(it, color = if (row.over) colors.error else colors.onSurface) }
                }
                BudgetBar(m.spent, m.projected, m.limit!!, m.current, m.over, row.sub)
                Text(row.sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        val cats = rep.catRows(money)
        if (cats.isNotEmpty()) {
            Text("Category limits", style = MaterialTheme.typography.titleSmall)
            cats.forEach { Row2(it.category, it.sub, it.value, if (it.over) colors.error else SpendWiseTheme.moneyColors.moneyIn, categoryIcon(it.category)) }
        }
        Muted("Measured against the budget you have now.")
        TextButton(onClick = onBudget, contentPadding = PaddingValues(0.dp)) { Text("Change budget") }
    }
}

/** Spent (solid), where the current month is heading (lighter, behind) and a tick at the limit. */
@Composable
private fun BudgetBar(spent: Double, projected: Double, limit: Double, current: Boolean, over: Boolean, label: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val done = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val ahead = lerp(MaterialTheme.colorScheme.surfaceContainerLow, done, 0.4f)
    val tick = MaterialTheme.colorScheme.onSurface
    val max = maxOf(limit, projected).takeIf { it != 0.0 } ?: 1.0
    fun w(v: Double) = minOf(v / max, 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(10.dp).clearAndSetSemantics { contentDescription = label }) {
        drawRect(track)
        if (current) drawRect(ahead, size = size.copy(width = w(projected) * size.width))
        drawRect(done, size = size.copy(width = w(spent) * size.width))
        val x = w(limit) * size.width
        drawLine(tick, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), 2.dp.toPx())
    }
}

// Pie colours, from the web's tokens.css (--pie-1…5, --pie-other), light then dark
private val PIE_LIGHT = listOf(0xFF2A78D6, 0xFFC9501A, 0xFF16805C, 0xFFA06A00, 0xFFC2407A).map(::Color) to Color(0xFF6E695E)
private val PIE_DARK = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181).map(::Color) to Color(0xFF77716A)
private const val PIE_GAP = 0.6 // of 100: a little surface between slices

@Composable
private fun PieSection(slices: List<PieSlice>, spent: Double, money: (Double) -> String) {
    val (palette, other) = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) PIE_DARK else PIE_LIGHT
    val colour = { i: Int -> if (slices[i].other) other else palette[i] }
    val one = slices.size == 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Where it went", "by category")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(132.dp).clearAndSetSemantics {
                    contentDescription = "Spending by category: " + slices.joinToString(", ") { "${it.label} ${toFixed0(it.share)}%" }
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val inset = stroke / 2
                    slices.forEachIndexed { i, s ->
                        val len = if (one) 100.0 else maxOf(s.share - PIE_GAP, 0.3)
                        val startAt = s.start + if (one) 0.0 else PIE_GAP / 2
                        drawArc(
                            colour(i), (-90 + startAt * 3.6).toFloat(), (len * 3.6).toFloat(), useCenter = false,
                            topLeft = Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Butt),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(money(spent), style = MaterialTheme.typography.titleSmall)
                    Text("spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slices.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(colour(i), CircleShape))
                        Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(s.amount), style = MaterialTheme.typography.bodyMedium)
                            Text(s.shareText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangedSection(s: InsightSummary, money: (Double) -> String) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Title("What changed", "by category")
    s.movers.forEach { m ->
        Row2(m.category, m.sub(money), m.value(money), if (m.diff > 0) MaterialTheme.colorScheme.error else SpendWiseTheme.moneyColors.moneyIn, categoryIcon(m.category))
    }
}

@Composable
private fun BiggestSection(s: InsightSummary, money: (Double) -> String) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Title("Biggest expenses")
    s.biggest.forEach { Row2(it.title(), it.meta(), money(it.amount)) }
}

@Composable
private fun WeekdaySection(s: InsightSummary, w: com.spendwise.app.domain.WeekdayCard, money: (Double) -> String) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("When you spend")
        Text(w.lead, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.weekdays.forEachIndexed { i, d ->
                val top = d == w.top
                Column(
                    Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = "${d.day}: ${money(d.amount)}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.weight(1f).width(20.dp)) {
                        drawRect(colors.surfaceVariant)
                        val h = (w.heights[i] / 100 * size.height).toFloat()
                        drawRect(if (top) colors.primary else colors.onSurfaceVariant, Offset(0f, size.height - h), size.copy(height = h))
                    }
                    Text(d.day.take(1), style = MaterialTheme.typography.labelMedium, color = if (top) colors.primary else colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PaymentSection(rows: List<Pair<com.spendwise.app.domain.PaymentShare, String>>) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Title("How you pay")
    rows.forEach { (p, text) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(paymentIcon(p.name))
                Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            StackBar(listOf(maxOf(p.share, 2.0) / 100 to MaterialTheme.colorScheme.primary), "${p.name}: $text", height = 6)
        }
    }
}

@Composable
private fun FixedSection(f: com.spendwise.app.domain.FixedCard) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Title("Fixed and flexible")
    val fixed = shade(1f)
    val flexible = shade(0.3f)
    StackBar(listOf(f.share / 100 to fixed, (1 - f.share / 100) to flexible), "${toFixed0(f.share)}% fixed, ${toFixed0(100 - f.share)}% flexible")
    Legend(fixed, f.fixed.first, f.fixed.second)
    Legend(flexible, f.flexible.first, f.flexible.second)
}

// ── Ask about your spending ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskSection(ask: AskView?, asking: Boolean, onAsk: (String) -> Unit, money: (Double) -> String, onOpenHistory: (AskHistory) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Ask about your spending")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(ASK_MAX) },
            placeholder = { Text("How much did I spend on Amazon last month?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onAsk(text) }),
            trailingIcon = {
                TextButton(onClick = { onAsk(text) }, enabled = !asking) { Text(if (asking) "Working it out" else "Ask") }
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Your question" },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ASK_EXAMPLES.forEach { q -> AssistChip(onClick = { text = q; onAsk(q) }, label = { Text(q) }) }
        }
        if (asking) Muted("Looking through your entries…")
        ask ?: return@Column
        val answer = answerFor(ask.spec, ask.result, money, ask.today)
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(answer.headline, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(answer.sentence, style = MaterialTheme.typography.bodyLarge)
            Muted("Searched: ${searchedText(ask.spec, ask.today)}")
        }
        askRows(ask.spec, ask.result).forEach { r ->
            val sub = listOf(if (r is Entry.Earned) "Income" else r.kind, formatDate(r.date)).filter { it.isNotEmpty() }.joinToString(" · ")
            Row2(askName(r).ifEmpty { if (r is Entry.Earned) "Income" else "" }, sub, money(r.amount))
        }
        val link = askHistory(ask.spec)
        if (link != null && ask.result.count > 0) {
            TextButton(onClick = { onOpenHistory(link) }) {
                Text(if (ask.result.count > 5) "See all ${ask.result.count} in History" else "Open in History")
            }
        }
    }
}

