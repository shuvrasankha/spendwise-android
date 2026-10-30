package com.spendwise.app.ui.insights

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.ASK_EXAMPLES
import com.spendwise.app.domain.ASK_MAX
import com.spendwise.app.domain.AskHistory
import com.spendwise.app.domain.BudgetReport
import com.spendwise.app.domain.Change
import com.spendwise.app.domain.Entry
import com.spendwise.app.domain.FixedCard
import com.spendwise.app.domain.Forecast
import com.spendwise.app.domain.InsightFigure
import com.spendwise.app.domain.InsightPeriod
import com.spendwise.app.domain.InsightSummary
import com.spendwise.app.domain.PaymentShare
import com.spendwise.app.domain.PieSlice
import com.spendwise.app.domain.WeekdayCard
import com.spendwise.app.domain.answerFor
import com.spendwise.app.domain.askHistory
import com.spendwise.app.domain.askName
import com.spendwise.app.domain.askRows
import com.spendwise.app.domain.catRows
import com.spendwise.app.domain.compareCard
import com.spendwise.app.domain.figure
import com.spendwise.app.domain.fixedCard
import com.spendwise.app.domain.forecastCard
import com.spendwise.app.domain.meta
import com.spendwise.app.domain.monthRows
import com.spendwise.app.domain.paymentRows
import com.spendwise.app.domain.searchedText
import com.spendwise.app.domain.shareText
import com.spendwise.app.domain.sub
import com.spendwise.app.domain.title
import com.spendwise.app.domain.value
import com.spendwise.app.domain.weekdayCard
import com.spendwise.app.ui.components.BarSegment
import com.spendwise.app.ui.components.BudgetBar
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.DateField
import com.spendwise.app.ui.components.LegendRow
import com.spendwise.app.ui.components.ListRow
import com.spendwise.app.ui.components.PickDateDialog
import com.spendwise.app.ui.components.SectionHeader
import com.spendwise.app.ui.components.Stat
import com.spendwise.app.ui.components.StatGrid
import com.spendwise.app.ui.components.StackedBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.TabTopBar
import com.spendwise.app.ui.components.Toast
import com.spendwise.app.ui.components.WeekBars
import com.spendwise.app.ui.components.show
import com.spendwise.app.ui.dashboard.PlanDialogs
import com.spendwise.app.ui.dashboard.PlansViewModel
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** Insights (spec §1.7): Ask (read on the device), the forecast, the period chips, then the period's cards in the web's order. */
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
    val money = { n: Double -> formatAmount(n, currency) }
    val snackbar = remember { SnackbarHostState() }
    val notice by plans.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        plans.noticeShown()
        snackbar.show(Toast(n.text, n.error))
    }
    PlanDialogs(plans, currency)
    val c = SpendWiseTheme.colors

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Spacing.section)) {
            item { TabTopBar("Insights") }
            when (val s = state) {
                InsightsState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp).semantics { contentDescription = "Adding up your numbers" }, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.text)
                    }
                }
                is InsightsState.Failed -> item {
                    Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(s.message, style = SwType.body, color = c.negative)
                        SwButton("Try again", insights::retry, style = ButtonStyle.Secondary)
                    }
                }
                is InsightsState.Ready -> {
                    item { AskSection(s.ask, asking, insights::ask, money, onOpenHistory) }
                    item { Section { Periods(s.choice, insights) } }
                    item { Section { ForecastSection(s.forecast, money) } }
                    val g = s.glance
                    when {
                        s.periodError != null -> item { Section { Note(s.periodError, c.negative) } }
                        g == null -> Unit
                        !g.summary.hasData -> item {
                            Section {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Note("Nothing recorded in ${g.range.label}. Try a longer period, or add an expense.")
                                    Box(Modifier.padding(horizontal = 8.dp)) { SwButton("Add an expense", onAddExpense, style = ButtonStyle.Text, compact = true) }
                                }
                            }
                        }
                        else -> {
                            item { Section { CompareSection(g.summary, g, money) } }
                            item { Section { BudgetSection(g.budget, money, plans::openBudget) } }
                            if (g.pie.isNotEmpty()) item { Section { PieSection(g.pie, g.summary.spent, money) } }
                            if (g.summary.movers.isNotEmpty()) item { Section { ChangedSection(g.summary, money) } }
                            if (g.summary.biggest.isNotEmpty()) item { Section { BiggestSection(g.summary, money) } }
                            weekdayCard(g.summary, money)?.let { w -> item { Section { WeekdaySection(g.summary, w, money) } } }
                            paymentRows(g.summary, money).takeIf { it.isNotEmpty() }?.let { rows -> item { Section { PaymentSection(rows) } } }
                            fixedCard(g.summary, money)?.let { f -> item { Section { FixedSection(f) } } }
                        }
                    }
                    item { Section { CoachSection(coachState, g.takeIf { s.periodError == null }, coach) } }
                }
            }
        }
    }
}

// ── Shared pieces ──

@Composable
private fun Section(content: @Composable () -> Unit) = Box(Modifier.padding(top = Spacing.section)) { content() }

@Composable
private fun Note(text: String, color: Color = Color.Unspecified) =
    Text(text, style = SwType.label, color = if (color == Color.Unspecified) SpendWiseTheme.colors.textMuted else color, modifier = Modifier.padding(horizontal = Spacing.gutter))

@Composable
private fun BigFigure(f: InsightFigure) = Row(Modifier.padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(f.amount, style = SwType.title, color = SpendWiseTheme.colors.text)
    Text(f.unit, style = SwType.label, color = SpendWiseTheme.colors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
}

/** Spending going up is the "over" colour; going down reads as plain text. The words say which way too. */
@Composable
private fun ChangeLine(c: Change) = Note(c.text, if (c.up) SpendWiseTheme.colors.negative else Color.Unspecified)

/** Greys from the text colour down, so slices are told apart without borrowing the accent. */
@Composable
private fun shades(n: Int): List<Color> {
    val c = SpendWiseTheme.colors
    return List(n) { i -> if (i == n - 1 && n > 1) c.textFaint else c.text.copy(alpha = (1f - i * 0.17f).coerceAtLeast(0.35f)) }
}

// ── Ask ──

@Composable
private fun AskSection(ask: AskView?, asking: Boolean, onAsk: (String) -> Unit, money: (Double) -> String, onOpenHistory: (AskHistory) -> Unit) {
    val c = SpendWiseTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(horizontal = Spacing.gutter)) {
            SwField(
                text, { text = it.take(ASK_MAX) }, "Ask about your spending", leading = SwIcons.Search,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAsk(text) }),
                trailing = { SwButton(if (asking) "Working…" else "Ask", { onAsk(text) }, style = ButtonStyle.Text, compact = true, enabled = !asking) },
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ASK_EXAMPLES.forEach { q -> SwChip(q, false, { text = q; onAsk(q) }) }
        }
        if (asking) Note("Looking through your entries…")
        ask ?: return@Column
        val answer = answerFor(ask.spec, ask.result, money, ask.today)
        Column(
            Modifier.padding(horizontal = Spacing.gutter).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(answer.headline, style = SwType.title, color = c.text)
            Text(answer.sentence, style = SwType.body, color = c.text)
            Text("Searched: ${searchedText(ask.spec, ask.today)}", style = SwType.caption, color = c.textMuted)
        }
        askRows(ask.spec, ask.result).forEachIndexed { i, r ->
            val sub = listOf(if (r is Entry.Earned) "Income" else r.kind, formatDate(r.date)).filter { it.isNotEmpty() }.joinToString(" · ")
            ListRow(askName(r).ifEmpty { if (r is Entry.Earned) "Income" else "" }, meta = sub, amount = money(r.amount), divider = i > 0)
        }
        val link = askHistory(ask.spec)
        if (link != null && ask.result.count > 0) {
            Box(Modifier.padding(horizontal = 8.dp)) {
                SwButton(if (ask.result.count > 5) "See all ${ask.result.count} in History" else "Open in History", { onOpenHistory(link) }, style = ButtonStyle.Text, compact = true)
            }
        }
    }
}

// ── Period chips ──

@Composable
private fun Periods(choice: PeriodChoice, vm: InsightsViewModel) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Five periods don't fit a segmented control (2 to 4), so they scroll as chips
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightPeriod.entries.forEach { p -> SwChip(p.label, choice.period == p, { vm.select(p) }) }
        }
        if (choice.period == InsightPeriod.CUSTOM) {
            Row(Modifier.padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("From", formatDate(choice.customStart), Modifier.weight(1f)) { picking = "start" }
                DateField("To", formatDate(choice.customEnd), Modifier.weight(1f)) { picking = "end" }
            }
        }
    }
    when (picking) {
        "start" -> PickDateDialog(choice.customStart, vm::setCustomStart) { picking = null }
        "end" -> PickDateDialog(choice.customEnd, vm::setCustomEnd) { picking = null }
    }
}

// ── Forecast ──

@Composable
private fun ForecastSection(f: Forecast, money: (Double) -> String) {
    val card = forecastCard(f, money) ?: return
    val c = SpendWiseTheme.colors
    val colors = shades(card.parts.size)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Forecast", style = SwType.label, color = c.textMuted)
            Text(card.figure.amount, style = SwType.display, color = c.text)
            Text(card.figure.unit, style = SwType.label, color = c.textMuted)
        }
        card.vsLastMonth?.let { ChangeLine(it) }
        Box(
            Modifier.padding(horizontal = Spacing.gutter).semantics {
                contentDescription = "Spent so far ${money(f.spent)}, bills still due ${money(f.billsTotal)}, at your usual pace ${money(f.usualLeft)}"
            },
        ) { StackedBar(card.parts.mapIndexed { i, w -> BarSegment(w.toFloat(), colors[i]) }) }
        Column { card.legend.forEachIndexed { i, (title, sub) -> LegendRow(colors[i], title, sub) } }
        HorizontalDivider(Modifier.padding(horizontal = Spacing.gutter), color = c.line)
        Row(Modifier.padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
            Text(card.nextName, style = SwType.body, color = c.textMuted, modifier = Modifier.weight(1f))
            Text(card.nextTotal, style = SwType.bodyStrong, color = c.text)
        }
        Note(card.nextNote)
    }
}

// ── Cards ──

@Composable
private fun CompareSection(s: InsightSummary, g: Glance, money: (Double) -> String) {
    val card = compareCard(s, g.range, money)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(card.title)
        card.none?.let { Note(it) }
        card.figure?.let { BigFigure(it) }
        card.change?.let { ChangeLine(it) }
        if (card.rows.isNotEmpty()) StatGrid(card.rows.map { (k, v) -> Stat(k, v) })
    }
}

@Composable
private fun BudgetSection(rep: BudgetReport?, money: (Double) -> String, onBudget: () -> Unit) {
    val c = SpendWiseTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Against your budget", action = if (rep != null) "Change budget" else null, onAction = onBudget)
        if (rep == null) {
            Note("See how each month goes against a plan.")
            Box(Modifier.padding(horizontal = 8.dp)) { SwButton("Set a monthly budget", onBudget, style = ButtonStyle.Text, compact = true) }
            return@Column
        }
        rep.figure(money)?.let { BigFigure(it) }
        rep.monthRows(money).zip(rep.months).forEachIndexed { i, (row, m) ->
            Column {
                ListRow(
                    row.label + if (row.current) " so far" else "", meta = row.sub, metaLines = 2,
                    amount = row.status, amountColor = if (row.over) c.negative else Color.Unspecified, divider = i > 0,
                )
                // The bar's end is whichever is larger of the limit, the projection and what's spent; the tick is the limit
                val max = maxOf(m.limit!!, m.projected, m.spent).takeIf { it > 0 } ?: 1.0
                Box(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = 12.dp)) {
                    BudgetBar((m.spent / max).toFloat(), pace = (m.limit / max).toFloat(), over = m.over)
                }
            }
        }
        val cats = rep.catRows(money)
        if (cats.isNotEmpty()) {
            SectionHeader("Category limits")
            cats.forEachIndexed { i, r ->
                ListRow(r.category, meta = r.sub, amount = r.value, amountColor = if (r.over) c.negative else Color.Unspecified, icon = SwIcons.forCategory(r.category), divider = i > 0)
            }
        }
        Note("Measured against the budget you have now.")
    }
}

@Composable
private fun PieSection(slices: List<PieSlice>, spent: Double, money: (Double) -> String) {
    val colors = shades(slices.size)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Where it went")
        Note("${money(spent)} spent, by category")
        Box(
            Modifier.padding(horizontal = Spacing.gutter).semantics {
                contentDescription = "Spending by category: " + slices.joinToString(", ") { "${it.label} ${it.shareText()}" }
            },
        ) { StackedBar(slices.mapIndexed { i, s -> BarSegment(s.share.toFloat(), colors[i]) }) }
        Column { slices.forEachIndexed { i, s -> LegendRow(colors[i], s.label, s.shareText(), trailing = money(s.amount)) } }
    }
}

@Composable
private fun ChangedSection(s: InsightSummary, money: (Double) -> String) = Column {
    SectionHeader("What changed")
    s.movers.forEachIndexed { i, m ->
        ListRow(
            m.category, meta = m.sub(money), amount = m.value(money), amountColor = if (m.diff > 0) SpendWiseTheme.colors.negative else Color.Unspecified,
            icon = SwIcons.forCategory(m.category), divider = i > 0,
        )
    }
}

@Composable
private fun BiggestSection(s: InsightSummary, money: (Double) -> String) = Column {
    SectionHeader("Biggest expenses")
    s.biggest.forEachIndexed { i, b -> ListRow(b.title(), meta = b.meta(), amount = money(b.amount), icon = SwIcons.forCategory(b.category), divider = i > 0) }
}

@Composable
private fun WeekdaySection(s: InsightSummary, w: WeekdayCard, money: (Double) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("When you spend")
        Note(w.lead)
        Box(
            Modifier.padding(horizontal = Spacing.gutter).semantics {
                contentDescription = "Spending by weekday: " + s.weekdays.joinToString(", ") { "${it.day} ${money(it.amount)}" }
            },
        ) { WeekBars(s.weekdays.map { it.amount.toFloat() }, s.weekdays.map { it.day.take(3) }, s.weekdays.indexOf(w.top)) }
    }
}

private fun paymentIcon(method: String) = when (method) {
    "UPI" -> SwIcons.Smartphone
    "Cash" -> SwIcons.Banknote
    "Credit Card" -> SwIcons.CreditCard
    "Net Banking" -> SwIcons.Landmark
    else -> SwIcons.Wallet
}

@Composable
private fun PaymentSection(rows: List<Pair<PaymentShare, String>>) {
    val c = SpendWiseTheme.colors
    Column {
        SectionHeader("How you pay")
        rows.forEachIndexed { i, (p, text) ->
            Column {
                ListRow(p.name, amount = text, icon = paymentIcon(p.name), divider = i > 0)
                Box(Modifier.padding(start = Spacing.gutter + 48.dp, end = Spacing.gutter, bottom = 12.dp)) {
                    StackedBar(listOf(BarSegment(maxOf(p.share, 2.0).toFloat(), c.textMuted), BarSegment((100 - maxOf(p.share, 2.0)).toFloat(), Color.Transparent)))
                }
            }
        }
    }
}

@Composable
private fun FixedSection(f: FixedCard) {
    val c = SpendWiseTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Fixed and flexible")
        Box(Modifier.padding(horizontal = Spacing.gutter).semantics { contentDescription = "${f.share.toInt()}% fixed, ${100 - f.share.toInt()}% flexible" }) {
            StackedBar(listOf(BarSegment((f.share / 100).toFloat(), c.text), BarSegment((1 - f.share / 100).toFloat(), c.textFaint)))
        }
        Column {
            LegendRow(c.text, f.fixed.first, f.fixed.second)
            LegendRow(c.textFaint, f.flexible.first, f.flexible.second)
        }
    }
}
