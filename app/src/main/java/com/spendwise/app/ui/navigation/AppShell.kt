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
import com.spendwise.app.ui.expenses.VoiceScreen
import com.spendwise.app.ui.expenses.EditExpenseScreen
import com.spendwise.app.domain.greeting
import com.spendwise.app.ui.components.BottomTab
import com.spendwise.app.ui.components.EntryType
import com.spendwise.app.ui.components.HomeTopBar
import com.spendwise.app.ui.components.SpendWiseBottomBar
import com.spendwise.app.ui.components.Toast
import com.spendwise.app.ui.components.show
import com.spendwise.app.ui.debts.DebtsViewModel
import com.spendwise.app.ui.entry.NewEntryHost
import com.spendwise.app.ui.expenses.ExpenseEditorViewModel
import com.spendwise.app.ui.income.IncomeViewModel
import com.spendwise.app.ui.invest.InvestViewModel
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime
import com.spendwise.app.ui.history.HistoryScreen
import com.spendwise.app.ui.more.MoreScreen
import com.spendwise.app.ui.income.EditIncomeScreen
import com.spendwise.app.ui.income.IncomeScreen
import com.spendwise.app.ui.settings.SettingsScreen
import com.spendwise.app.ui.settings.CsvImportScreen

/** The pages More links to, which keep More highlighted in the bottom bar. */
private val moreRoutes = listOf(More, Income, Investments, Debts, Settings, CsvImport)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(link: Any? = null, onLinkOpened: () -> Unit = {}, shell: ShellViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val header by shell.header.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var entry by rememberSaveable { mutableStateOf<EntryType?>(null) }
    // One ViewModel of each, shared by the sheet and the page, so a prefilled or half-typed form is the same on both
    val expenseVm: ExpenseEditorViewModel = hiltViewModel()
    val incomeVm: IncomeViewModel = hiltViewModel()
    val debtsVm: DebtsViewModel = hiltViewModel()
    val investVm: InvestViewModel = hiltViewModel()
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        // A shared bank SMS or text lands in the New entry sheet, which takes it from the inbox
        if (link == AddExpense) entry = EntryType.Expense else nav.openLink(link)
        onLinkOpened()
    }
    val destination = nav.currentBackStackEntryAsState().value?.destination
    val inMore = moreRoutes.any { destination?.hasRoute(it::class) == true }
    val onDashboard = destination?.hasRoute(Dashboard::class) == true
    val ownsTopBar = onDashboard || destination?.hasRoute(More::class) == true || destination?.hasRoute(History::class) == true || destination?.hasRoute(Insights::class) == true
    val snackbar = remember { SnackbarHostState() }
    val editor by expenseVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(editor.undo) {
        val added = editor.undo ?: return@LaunchedEffect
        expenseVm.undoShown()
        entry = null
        if (snackbar.showSnackbar(added.label, actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Long) == SnackbarResult.ActionPerformed) expenseVm.undo(added)
    }
    // While the sheet is open it takes the Invest confirmation itself
    val investToast by investVm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(investToast, entry) {
        val t = investToast ?: return@LaunchedEffect
        if (entry != null) return@LaunchedEffect
        investVm.toastShown()
        snackbar.show(t)
    }
    PlayUpdates(snackbar)
    GithubUpdates(snackbar)
    // Back from a tab goes to the Dashboard, even when the stack has no Dashboard under it (it would exit the app)
    val selectedTab = when {
        onDashboard -> BottomTab.Home
        destination?.hasRoute(History::class) == true -> BottomTab.History
        destination?.hasRoute(Insights::class) == true -> BottomTab.Insights
        inMore -> BottomTab.More
        else -> null
    }
    val underDashboard = nav.previousBackStackEntry?.destination?.hasRoute(Dashboard::class) == true
    // Back from a top-level tab goes to the Dashboard, even when the stack has no Dashboard under it (it would exit the app).
    // Pages opened from More or the Dashboard just pop.
    val onTopTab = selectedTab == BottomTab.History || selectedTab == BottomTab.Insights || destination?.hasRoute(More::class) == true
    BackHandler(enabled = onTopTab && !underDashboard) { nav.switchTo(Dashboard) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // These pages draw their own top bar as the first row of their list
        topBar = { if (!ownsTopBar) Header(header, onSettings = { nav.switchTo(Settings) }, onLogOut = shell::logOut, onOpenDebts = { nav.switchTo(Debts) }) },
        bottomBar = {
            SpendWiseBottomBar(
                selected = selectedTab,
                onSelect = {
                    when (it) {
                        BottomTab.Home -> nav.switchTo(Dashboard)
                        BottomTab.History -> nav.switchTo(History())
                        BottomTab.Insights -> nav.switchTo(Insights)
                        BottomTab.More -> nav.switchTo(More)
                    }
                },
                onAdd = { entry = EntryType.Expense },
            )
        },
    ) { padding ->
        // Consumed so a screen's imePadding doesn't add the keyboard on top of the bottom bar's padding
        NavHost(nav, startDestination = Dashboard, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<Dashboard> {
                DashboardScreen(
                    topBar = {
                        HomeTopBar(
                            greeting = header.status ?: "SpendWise",
                            name = greeting(LocalTime.now().hour, header.name),
                        ) { HeaderActions(header, onSettings = { nav.switchTo(Settings) }, onLogOut = shell::logOut, onOpenDebts = { nav.switchTo(Debts) }) }
                    },
                    onAddExpense = { entry = EntryType.Expense },
                    onEditExpense = { nav.navigate(EditExpense(it)) },
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
                    onAddExpense = { entry = EntryType.Expense },
                    onAddIncome = { entry = EntryType.Income },
                )
            }
            composable<Voice> { VoiceScreen(onDone = { nav.popBackStack() }) }
            composable<EditExpense> { EditExpenseScreen(onDone = { nav.popBackStack() }) }
            composable<EditIncome> { EditIncomeScreen(onDone = { nav.popBackStack() }) }
            composable<Insights> {
                InsightsScreen(
                    onAddExpense = { entry = EntryType.Expense },
                    onOpenHistory = { nav.navigate(History(income = it.income, q = it.q, from = it.from, to = it.to, cats = it.cats)) },
                )
            }
            composable<More> {
                MoreScreen(
                    onIncome = { nav.navigate(Income) },
                    onDebts = { nav.navigate(Debts) },
                    onInvestments = { nav.navigate(Investments) },
                    onGoals = { nav.switchTo(Dashboard) },
                    onImport = { nav.navigate(CsvImport) },
                    onSettings = { nav.navigate(Settings) },
                )
            }
            composable<Income> {
                IncomeScreen(onEdit = { nav.navigate(EditIncome(it)) }, onAllIncome = { nav.navigate(History(income = true)) }, income = incomeVm)
            }
            composable<Investments> { InvestScreen(onAllHistory = { nav.navigate(History()) }, vm = investVm) }
            composable<Debts> { DebtsScreen(vm = debtsVm) }
            composable<Settings> { SettingsScreen(onImport = { nav.navigate(CsvImport) }) }
            composable<CsvImport> { CsvImportScreen(onDone = { nav.popBackStack() }) }
        }
    }

    entry?.let { type ->
        NewEntryHost(
            type = type,
            onTypeChange = { entry = it },
            onDismiss = { entry = null },
            onSaved = { text -> entry = null; scope.launch { snackbar.show(Toast(text)) } },
            onVoice = { entry = null; nav.navigate(Voice) },
            onNewHolding = { entry = null; nav.switchTo(Investments) },
            expense = expenseVm, income = incomeVm, debts = debtsVm, invest = investVm,
        )
    }
}

/** The header on every signed-in page (spec §1.0): sync status, the bell, and the avatar menu with who's signed in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(header: HeaderState, onSettings: () -> Unit, onLogOut: () -> Unit, onOpenDebts: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                header.status ?: "SpendWise",
                style = if (header.status == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                color = if (header.status == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
        actions = { HeaderActions(header, onSettings, onLogOut, onOpenDebts) },
    )
}

/** The bell and the avatar menu with who's signed in, shared by [Header] and the Dashboard's top bar. */
@Composable
private fun HeaderActions(header: HeaderState, onSettings: () -> Unit, onLogOut: () -> Unit, onOpenDebts: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
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

