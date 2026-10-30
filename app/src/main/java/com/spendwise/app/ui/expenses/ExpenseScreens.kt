package com.spendwise.app.ui.expenses

import com.spendwise.app.ui.components.PanelShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.clip
import com.spendwise.app.ui.components.Panel
import com.spendwise.app.ui.components.show
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
