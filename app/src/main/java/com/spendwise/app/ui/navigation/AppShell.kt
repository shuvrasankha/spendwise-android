package com.spendwise.app.ui.navigation

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
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
import com.spendwise.app.ui.expenses.VoiceScreen
import com.spendwise.app.ui.expenses.EditExpenseScreen
import com.spendwise.app.ui.history.HistoryScreen
import com.spendwise.app.ui.income.EditIncomeScreen
import com.spendwise.app.ui.income.IncomeScreen
import com.spendwise.app.ui.settings.SettingsScreen
import com.spendwise.app.ui.settings.CsvImportScreen

private data class Tab(val route: Any, val label: String, @param:DrawableRes val icon: Int)

private val tabs = listOf(
    Tab(Dashboard, "Dashboard", R.drawable.ic_tab_dashboard),
    Tab(History(), "History", R.drawable.ic_tab_history),
    Tab(AddExpense, "Expense", R.drawable.ic_tab_wallet),
    Tab(Insights, "Insights", R.drawable.ic_tab_insights),
)

private data class More(val route: Any, val label: String, @param:DrawableRes val icon: Int)

private val moreItems = listOf(
    More(Income, "Income", R.drawable.ic_cat_income), More(Investments, "Investments", R.drawable.ic_tab_insights),
    More(Debts, "Debts", R.drawable.ic_debt), More(Settings, "Settings", R.drawable.ic_settings),
)

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
    val inMore = moreItems.any { destination?.hasRoute(it.route::class) == true }
    val snackbar = remember { SnackbarHostState() }
    PlayUpdates(snackbar)
    GithubUpdates(snackbar)
    // Back from a tab goes to the Dashboard, even when the stack has no Dashboard under it (it would exit the app)
    val onTab = inMore || tabs.any { destination?.hasRoute(it.route::class) == true }
    val underDashboard = nav.previousBackStackEntry?.destination?.hasRoute(Dashboard::class) == true
    BackHandler(enabled = onTab && destination?.hasRoute(Dashboard::class) != true && !underDashboard) { nav.switchTo(Dashboard) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { Header(header, onSettings = { nav.switchTo(Settings) }, onLogOut = shell::logOut, onOpenDebts = { nav.switchTo(Debts) }) },
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
                    onOpenHistory = { from, to -> nav.navigate(History(from = from, to = to)) },
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
            composable<AddExpense> { AddExpenseScreen(onEdit = { nav.navigate(EditExpense(it)) }, onVoice = { nav.navigate(Voice) }) }
            composable<Voice> { VoiceScreen(onDone = { nav.popBackStack() }) }
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
            composable<Settings> { SettingsScreen(onImport = { nav.navigate(CsvImport) }) }
            composable<CsvImport> { CsvImportScreen(onDone = { nav.popBackStack() }) }
        }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }, tonalElevation = 0.dp) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                moreItems.forEach { (route, label, icon) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
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

/** The header on every signed-in page (spec §1.0): sync status, the bell, and the avatar menu with who's signed in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(header: HeaderState, onSettings: () -> Unit, onLogOut: () -> Unit, onOpenDebts: () -> Unit) {
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
            Bell(onOpenDebts)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Account menu" }) {
                    Avatar(header.name.ifEmpty { header.email }, size = 32.dp, photoUrl = header.photoUrl)
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

/**
 * History opens a fresh copy so its filters apply. The rest switch to their tab: a fresh Add would lose
 * shared text to the old copy's ViewModel, which takes it first.
 */
private fun NavHostController.openLink(route: Any) =
    if (route is History) navigate(route) { popUpTo(graph.findStartDestination().id) { saveState = true } } else switchTo(route)

/** Tab-style navigation: one copy of each destination, state kept when switching back. */
private fun NavHostController.switchTo(route: Any) {
    // The Dashboard is the base of the stack: pop back to it. Navigating "to" it while popping to it
    // never lands when History was opened on top of it by Show more.
    if (route == Dashboard && popBackStack(Dashboard, inclusive = false)) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

