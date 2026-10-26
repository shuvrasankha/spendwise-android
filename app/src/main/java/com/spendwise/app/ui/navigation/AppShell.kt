package com.spendwise.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendwise.app.R
import com.spendwise.app.ui.expenses.AddExpenseScreen
import com.spendwise.app.ui.expenses.EditExpenseScreen
import com.spendwise.app.ui.expenses.ExpenseListScreen
import com.spendwise.app.ui.settings.SettingsScreen

private data class Tab(val route: Any, val label: String, @param:DrawableRes val icon: Int)

private val tabs = listOf(
    Tab(Dashboard, "Dashboard", R.drawable.ic_tab_dashboard),
    Tab(History, "History", R.drawable.ic_tab_history),
    Tab(AddExpense, "Add", R.drawable.ic_tab_add),
    Tab(Insights, "Insights", R.drawable.ic_tab_insights),
)

private val moreItems = listOf(Income to "Income", Investments to "Investments", Debts to "Debts", Settings to "Settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell() {
    val nav = rememberNavController()
    val destination = nav.currentBackStackEntryAsState().value?.destination
    var showMore by rememberSaveable { mutableStateOf(false) }
    val inMore = moreItems.any { (route, _) -> destination?.hasRoute(route::class) == true }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hasRoute(tab.route::class) == true,
                        onClick = { nav.switchTo(tab.route) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
                NavigationBarItem(
                    selected = inMore,
                    onClick = { showMore = true },
                    icon = { Icon(painterResource(R.drawable.ic_tab_more), contentDescription = null) },
                    label = { Text("More") },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Dashboard, modifier = Modifier.padding(padding)) {
            composable<Dashboard> { Placeholder("Dashboard") }
            composable<History> { ExpenseListScreen(onEdit = { nav.navigate(EditExpense(it)) }) }
            composable<AddExpense> { AddExpenseScreen(onEdit = { nav.navigate(EditExpense(it)) }) }
            composable<EditExpense> { EditExpenseScreen(onDone = { nav.popBackStack() }) }
            composable<Insights> { Placeholder("Insights") }
            composable<Income> { Placeholder("Income") }
            composable<Investments> { Placeholder("Investments") }
            composable<Debts> { Placeholder("Debts") }
            composable<Settings> { SettingsScreen() }
        }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }, tonalElevation = 0.dp) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                moreItems.forEach { (route, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        modifier = Modifier.clickable {
                            showMore = false
                            nav.switchTo(route)
                        },
                    )
                }
            }
        }
    }
}

/** Tab-style navigation: one copy of each destination, state kept when switching back. */
private fun NavHostController.switchTo(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}
