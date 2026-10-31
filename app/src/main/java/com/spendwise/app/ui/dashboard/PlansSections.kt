package com.spendwise.app.ui.dashboard

import com.spendwise.app.ui.components.BudgetBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.DueBill
import com.spendwise.app.domain.GoalLevel
import com.spendwise.app.domain.HomeFigures
import com.spendwise.app.domain.billTitle
import com.spendwise.app.domain.goalsNeed
import com.spendwise.app.domain.meta
import com.spendwise.app.ui.budget.BudgetDialog
import com.spendwise.app.ui.goals.GoalDialog
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.ListRow
import com.spendwise.app.ui.components.SectionHeader
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.LocalDate

/** "Due this month": repeating bills waiting for one tap (dashboard.js › renderDue). */
@Composable
internal fun DueThisMonth(due: List<DueBill>, money: (Double) -> String, plans: PlansViewModel) {
    val today = LocalDate.now()
    Column {
        SectionHeader("Due this month")
        Text(money(due.sumOf { it.template.amount }) + " to add", style = SwType.label, color = SpendWiseTheme.colors.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter))
        due.forEachIndexed { i, d ->
            ListRow(billTitle(d.template), meta = d.meta(today), amount = money(d.template.amount), icon = SwIcons.Receipt, divider = i > 0)
            // Lined up with the goal's text and bar, not the icon
            Row(Modifier.padding(start = Spacing.gutter + 48.dp, end = Spacing.gutter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SwButton("Add", { plans.addDue(d) }, style = ButtonStyle.Secondary, compact = true)
                SwButton("Skip month", { plans.skip(d) }, style = ButtonStyle.Text, compact = true)
                SwButton("Stop repeating", { plans.stop(d) }, style = ButtonStyle.Text, compact = true)
            }
        }
    }
}

/** "Savings goals" (dashboard.js › renderGoals). */
@Composable
internal fun Goals(h: HomeFigures, money: (Double) -> String, plans: PlansViewModel, onOpenInvest: () -> Unit) {
    val c = SpendWiseTheme.colors
    Column {
        SectionHeader("Savings goals", action = "New goal", onAction = plans::newGoal, actionIcon = SwIcons.Plus)
        goalsNeed(h.goals, h.saved, money)?.let {
            Text(it, style = SwType.label, color = if (it.endsWith("covered")) c.positive else c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter))
        }
        if (h.goals.isEmpty()) {
            Text(
                "Saving for a trip, a phone or a rainy day? Add a goal to see how much to put aside each month.",
                style = SwType.body, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.rowV),
            )
        }
        h.goals.forEachIndexed { i, row ->
            val g = row.goal
            val done = row.pace.level == GoalLevel.DONE
            ListRow(
                g.name, meta = row.meta(money), metaColor = if (row.pace.level == GoalLevel.LATE) c.negative else Color.Unspecified,
                amount = if (done) "Done" else "${row.percent}%", amountColor = if (done) c.positive else Color.Unspecified,
                sub = row.amounts(money), icon = SwIcons.Target, divider = i > 0, metaLines = 2,
            )
            Box(Modifier.padding(start = Spacing.gutter + 48.dp, end = Spacing.gutter, bottom = 4.dp)) { BudgetBar(row.percent / 100f) }
            // Lined up with the goal's text and bar, not the icon
            Row(Modifier.padding(start = Spacing.gutter + 48.dp, end = Spacing.gutter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!done) SwButton("Add money", { plans.addMoney(g.id) }, style = ButtonStyle.Secondary, compact = true)
                SwButton("Edit", { plans.editGoal(g.id) }, style = ButtonStyle.Text, compact = true)
                if (row.linked > 0) SwButton("${money(row.linked)} invested", onOpenInvest, style = ButtonStyle.Text, compact = true)
            }
        }
    }
}

@Composable
internal fun PlanDialogs(plans: PlansViewModel, currency: Currency) {
    val budgetOpen by plans.budgetOpen.collectAsStateWithLifecycle()
    val budgetError by plans.budgetError.collectAsStateWithLifecycle()
    val goal by plans.goal.collectAsStateWithLifecycle()
    val goalError by plans.goalError.collectAsStateWithLifecycle()
    val suggest by plans.suggest.collectAsStateWithLifecycle()
    if (budgetOpen) BudgetDialog(plans.budget, currency, budgetError, suggest, plans::suggestBudget, plans::saveBudget, plans::removeBudget, plans::closeBudget)
    goal?.let { GoalDialog(it, currency, goalError, plans::submitGoal, plans::deleteGoal, plans::closeGoal) }
}
