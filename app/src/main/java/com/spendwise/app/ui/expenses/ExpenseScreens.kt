package com.spendwise.app.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.R
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Expense
import com.spendwise.app.ui.format.formatDate
import com.spendwise.app.ui.format.formatMoney

/** Add tab (spec §1.3): the form, then today's entries. */
@Composable
fun AddExpenseScreen(
    onEdit: (String) -> Unit,
    editor: ExpenseEditorViewModel = hiltViewModel(),
    expenses: ExpensesViewModel = hiltViewModel(),
) {
    val state by editor.state.collectAsStateWithLifecycle()
    val currency by editor.currency.collectAsStateWithLifecycle()
    val list by expenses.list.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.undo) {
        val added = state.undo ?: return@LaunchedEffect
        editor.undoShown()
        val result = snackbar.showSnackbar(added.label, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) editor.undo(added)
    }
    ListMessages(expenses, snackbar)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("Add expense", style = MaterialTheme.typography.headlineSmall) }
            item { ExpenseFormFields(state.form, state.tiles, currency, editor) }
            // The web's collapsed "More details", whose summary is the payment method
            item {
                Collapsible("More details", state.form.payment) {
                    ExpenseDetailFields(state.form, editor) { RepeatFields(state.form, adding = true, editor) }
                }
            }
            item { SplitFields(state, currency, editor) }
            item { FormMessageText(state.message) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { editor.clear() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                    Button(onClick = editor::save, modifier = Modifier.weight(1f)) { Text("Add Expense") }
                }
            }
            item {
                HorizontalDivider()
                Text("Today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
            expenseItems(list, currency, onEdit, expenses, empty = "Nothing added today yet.") { it.today }
            (list as? ExpenseList.Ready)?.let { ready ->
                item { ExpenseSummary(ready.figures, currency) }
                ready.emis.takeIf { it.isNotEmpty() }?.let { plans -> item { EmiCard(plans, currency) } }
            }
        }
    }
    ConfirmDeleteFromList(expenses)
}

/** Edit an existing expense (spec §2.3): the same fields, Save, and Delete with confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(onDone: () -> Unit, editor: ExpenseEditorViewModel = hiltViewModel()) {
    val state by editor.state.collectAsStateWithLifecycle()
    val currency by editor.currency.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Edit expense") },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> Text(
                    "This expense was deleted or couldn't be loaded.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { ExpenseFormFields(state.form, state.tiles, currency, editor) }
                    item { ExpenseDetailFields(state.form, editor) { RepeatFields(state.form, adding = false, editor) } }
                    item { FormMessageText(state.message) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = editor::askDelete, modifier = Modifier.weight(1f)) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                            Button(onClick = editor::save, modifier = Modifier.weight(1f)) { Text("Save") }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmDelete) DeleteExpenseDialog(onConfirm = editor::confirmDelete, onDismiss = editor::cancelDelete)
}

/** Loading, error (with Try again), empty, or the rows. */
private fun LazyListScope.expenseItems(
    list: ExpenseList,
    currency: Currency,
    onEdit: (String) -> Unit,
    viewModel: ExpensesViewModel,
    empty: String,
    pick: (ExpenseList.Ready) -> List<Expense>,
) {
    when (list) {
        ExpenseList.Loading -> item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is ExpenseList.Failed -> item {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(list.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::retry) { Text("Try again") }
            }
        }
        is ExpenseList.Ready -> {
            val rows = pick(list)
            if (rows.isEmpty()) {
                item { Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(rows, key = { it.id }) { expense ->
                ExpenseRow(expense, currency, onEdit = { onEdit(expense.id) }, onDelete = { viewModel.askDelete(expense) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, currency: Currency, onEdit: () -> Unit, onDelete: () -> Unit) {
    val details = listOf(formatDate(expense.date), expense.category, expense.payment).filter { it.isNotEmpty() }.joinToString(" · ")
    ListItem(
        headlineContent = { Text(expense.description.ifEmpty { "-" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (expense.tags.isNotEmpty()) Text(expense.tags.joinToString(" ") { "#$it" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Offline writes can't be edited until they sync (spec §2.3)
                if (expense.pending) Text("Waiting to sync", color = MaterialTheme.colorScheme.primary)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMoney(expense.amount, currency), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (!expense.pending) {
                    IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete") }
                }
            }
        },
        modifier = if (expense.pending) Modifier else Modifier.clickable(onClickLabel = "Edit", onClick = onEdit),
    )
}

@Composable
private fun ConfirmDeleteFromList(viewModel: ExpensesViewModel) {
    val target by viewModel.confirmDelete.collectAsStateWithLifecycle()
    if (target != null) DeleteExpenseDialog(onConfirm = viewModel::confirmDelete, onDismiss = viewModel::cancelDelete)
}

@Composable
private fun ListMessages(viewModel: ExpensesViewModel, snackbar: SnackbarHostState) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        viewModel.messageShown()
        snackbar.showSnackbar(text)
    }
}
