package com.spendwise.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.components.Avatar
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.runtime.remember
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendwise.app.R
import com.spendwise.app.ui.debts.DebtsScreen
import com.spendwise.app.ui.insights.InsightsScreen
import com.spendwise.app.ui.invest.InvestScreen
import com.spendwise.app.ui.dashboard.DashboardScreen
import com.spendwise.app.ui.expenses.AddExpenseScreen
import com.spendwise.app.ui.expenses.EditExpenseScreen
import com.spendwise.app.ui.history.HistoryScreen
import com.spendwise.app.ui.income.EditIncomeScreen
import com.spendwise.app.ui.income.IncomeScreen
import com.spendwise.app.ui.settings.SettingsScreen

private data class Tab(val route: Any, val label: String, @param:DrawableRes val icon: Int)

private val tabs = listOf(
    Tab(Dashboard, "Dashboard", R.drawable.ic_tab_dashboard),
    Tab(History(), "History", R.drawable.ic_tab_history),
    Tab(AddExpense, "Add", R.drawable.ic_tab_add),
    Tab(Insights, "Insights", R.drawable.ic_tab_insights),
)

private val moreItems = listOf(Income to "Income", Investments to "Investments", Debts to "Debts", Settings to "Settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(link: Any? = null, onLinkOpened: () -> Unit = {}, shell: ShellViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val header by shell.header.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        nav.openLink(link)
        onLinkOpened()
    }
    val destination = nav.currentBackStackEntryAsState().value?.destination
    var showMore by rememberSaveable { mutableStateOf(false) }
    val inMore = moreItems.any { (route, _) -> destination?.hasRoute(route::class) == true }

    Scaffold(
        topBar = { Header(header, onSettings = { nav.switchTo(Settings) }, onLogOut = shell::logOut) },
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
        // Consumed so a screen's imePadding doesn't add the keyboard on top of the bottom bar's padding
        NavHost(nav, startDestination = Dashboard, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<Dashboard> {
                DashboardScreen(
                    onAddExpense = { nav.switchTo(AddExpense) },
                    onOpenIncome = { nav.switchTo(Income) },
                    onOpenDebts = { nav.switchTo(Debts) },
                    onOpenInvest = { nav.navigate(Investments) },
                )
            }
            composable<History> {
                HistoryScreen(
                    onEditExpense = { nav.navigate(EditExpense(it)) },
                    onEditIncome = { nav.navigate(EditIncome(it)) },
                    onAddExpense = { nav.switchTo(AddExpense) },
                    onAddIncome = { nav.switchTo(Income) },
                )
            }
            composable<AddExpense> { AddExpenseScreen(onEdit = { nav.navigate(EditExpense(it)) }) }
            composable<EditExpense> { EditExpenseScreen(onDone = { nav.popBackStack() }) }
            composable<EditIncome> { EditIncomeScreen(onDone = { nav.popBackStack() }) }
            composable<Insights> {
                InsightsScreen(
                    onAddExpense = { nav.switchTo(AddExpense) },
                    onOpenHistory = { nav.navigate(History(income = it.income, q = it.q, from = it.from, to = it.to, cats = it.cats)) },
                )
            }
            composable<Income> {
                IncomeScreen(onEdit = { nav.navigate(EditIncome(it)) }, onAllIncome = { nav.navigate(History(income = true)) })
            }
            composable<Investments> { InvestScreen(onAllHistory = { nav.navigate(History()) }) }
            composable<Debts> { DebtsScreen() }
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

/** The header on every signed-in page (spec §1.0): sync status, and the avatar menu with who's signed in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(header: HeaderState, onSettings: () -> Unit, onLogOut: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                header.status ?: "SpendWise",
                style = if (header.status == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                color = if (header.status == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
        actions = {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Account menu" }) {
                    Avatar(header.name.ifEmpty { header.email }, size = 32.dp)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(header.name.ifEmpty { "Your account" }, style = MaterialTheme.typography.titleSmall)
                        if (header.email.isNotEmpty()) {
                            Text(header.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                    DropdownMenuItem(text = { Text("Log out") }, onClick = { menu = false; onLogOut() })
                }
            }
        },
    )
}

/** A link opens a fresh copy of its page so its filters apply; the start page just switches to itself. */
private fun NavHostController.openLink(route: Any) =
    if (route == Dashboard) switchTo(route) else navigate(route) { popUpTo(graph.findStartDestination().id) { saveState = true } }

/** Tab-style navigation: one copy of each destination, state kept when switching back. */
private fun NavHostController.switchTo(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

