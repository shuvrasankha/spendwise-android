package com.spendwise.app.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.recentMeta
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.ListRow
import com.spendwise.app.ui.components.Metric
import com.spendwise.app.ui.components.SectionHeader
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.format.formatNumber
import com.spendwise.app.ui.format.plural
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Income (spec §1.4): this month so far, a button for the New entry sheet, and the last 8 entries. */
@Composable
fun IncomeScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onAllIncome: () -> Unit,
    income: IncomeViewModel = hiltViewModel(),
) {
    val page by income.page.collectAsStateWithLifecycle()
    val currency by income.currency.collectAsStateWithLifecycle()
    val c = SpendWiseTheme.colors

    Scaffold(contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Spacing.section)) {
            item { SubPageTopBar("Income", onBack) }
            val ready = page as? IncomePage.Ready
            item {
                val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.US)
                val s = ready?.summary
                Metric(
                    "$month so far", currency.symbol, if (s == null) "—" else formatNumber(s.monthTotal, currency),
                    sub = s?.let { "${plural(it.monthCount, "entry", "entries")} · ${formatAmount(it.yearTotal, currency)} this year" },
                    positive = true,
                )
            }
            item { Box(Modifier.padding(horizontal = Spacing.gutter, vertical = 24.dp)) { SwButton("Add income", onAdd, Modifier.fillMaxWidth(), leading = SwIcons.Plus) } }
            item { SectionHeader("Recent", action = if (ready != null && ready.all.size > ready.recent.size) "See all" else null, onAction = onAllIncome) }
            when (val p = page) {
                IncomePage.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.text) }
                }
                is IncomePage.Failed -> item {
                    Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.message, style = SwType.body, color = c.negative)
                        SwButton("Try again", income::retry, style = ButtonStyle.Secondary)
                    }
                }
                is IncomePage.Ready -> {
                    if (p.recent.isEmpty()) item {
                        Text("No income yet. Add your first one.", style = SwType.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter))
                    }
                    p.recent.forEachIndexed { i, inc ->
                        item(key = inc.id) {
                            ListRow(
                                inc.source.ifEmpty { "Income" },
                                meta = listOf(recentMeta(inc, LocalDate.now()), inc.notes).filter { it.isNotEmpty() }.joinToString("\n"), metaLines = 3,
                                // "+" says it's money in, as well as the colour
                                amount = "+" + formatAmount(inc.amount, currency), amountColor = c.positive, icon = SwIcons.MoneyIn, divider = i > 0,
                                // An entry waiting to sync has no server record yet, so it can't be edited
                                onClick = if (inc.pending) null else ({ onEdit(inc.id) }),
                            )
                        }
                    }
                }
            }
        }
    }
}
