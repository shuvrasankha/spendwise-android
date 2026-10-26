package com.spendwise.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.spendwise.app.ui.theme.SpendWiseTheme
import java.time.LocalDate

/** "Due this month": repeating bills waiting for one tap (dashboard.js › renderDue). */
@Composable
internal fun DueThisMonth(due: List<DueBill>, money: (Double) -> String, plans: PlansViewModel) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Text("Due this month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(money(due.sumOf { it.template.amount }), style = MaterialTheme.typography.titleMedium)
        }
        due.forEach { d ->
            Column {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(billTitle(d.template), style = MaterialTheme.typography.bodyLarge)
                        Text(d.meta(today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(money(d.template.amount), style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { plans.addDue(d) }) { Text("Add") }
                    TextButton(onClick = { plans.skip(d) }) { Text("Skip month") }
                    TextButton(onClick = { plans.stop(d) }) { Text("Stop repeating") }
                }
            }
        }
    }
}

/** "Savings goals" (dashboard.js › renderGoals). */
@Composable
internal fun Goals(h: HomeFigures, money: (Double) -> String, plans: PlansViewModel, onOpenInvest: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Savings goals", style = MaterialTheme.typography.titleMedium)
                goalsNeed(h.goals, h.saved, money)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (it.endsWith("covered")) SpendWiseTheme.moneyColors.moneyIn else colors.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = plans::newGoal) { Text("New goal") }
        }
        if (h.goals.isEmpty()) {
            Text("Saving for a trip, a phone or a rainy day? Add a goal to see how much to put aside each month.", color = colors.onSurfaceVariant)
        }
        h.goals.forEach { row ->
            val g = row.goal
            val done = row.pace.level == GoalLevel.DONE
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clearAndSetSemantics { contentDescription = "${g.name} saved ${row.percent}%" }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { row.percent / 100f },
                        color = if (done) SpendWiseTheme.moneyColors.moneyIn else colors.primary,
                        trackColor = colors.surfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(if (done) "✓" else "${row.percent}%", style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.bodyLarge)
                    Text(row.amounts(money), style = MaterialTheme.typography.bodyMedium)
                    if (row.linked > 0) {
                        TextButton(onClick = onOpenInvest) { Text("${money(row.linked)} invested") }
                    }
                    Text(
                        row.meta(money),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.pace.level == GoalLevel.LATE) colors.error else colors.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!done) OutlinedButton(onClick = { plans.addMoney(g.id) }) { Text("Add money") }
                TextButton(onClick = { plans.editGoal(g.id) }) { Text("Edit") }
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
    if (budgetOpen) BudgetDialog(plans.budget, currency, budgetError, plans::saveBudget, plans::removeBudget, plans::closeBudget)
    goal?.let { GoalDialog(it, currency, goalError, plans::submitGoal, plans::deleteGoal, plans::closeGoal) }
}
