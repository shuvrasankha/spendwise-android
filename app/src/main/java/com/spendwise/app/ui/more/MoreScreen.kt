package com.spendwise.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.ui.components.SettingsGroup
import com.spendwise.app.ui.components.SettingsRow
import com.spendwise.app.ui.components.TabTopBar
import com.spendwise.app.ui.components.Toast
import com.spendwise.app.ui.components.show
import com.spendwise.app.domain.moreSummary
import com.spendwise.app.ui.dashboard.DashboardState
import com.spendwise.app.ui.dashboard.DashboardViewModel
import com.spendwise.app.ui.dashboard.PlanDialogs
import com.spendwise.app.ui.format.formatWhole
import com.spendwise.app.ui.dashboard.PlansViewModel
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.settings.BackupDialog
import com.spendwise.app.ui.settings.BackupViewModel
import com.spendwise.app.ui.settings.SettingsViewModel
import com.spendwise.app.ui.theme.SpendWiseTheme

/** The page's links. A page opened from here pushes on top of it, so Back returns to More. */
@Composable
fun MoreScreen(
    onIncome: () -> Unit,
    onDebts: () -> Unit,
    onInvestments: () -> Unit,
    onGoals: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    plans: PlansViewModel = hiltViewModel(),
    dashboard: DashboardViewModel = hiltViewModel(),
    backup: BackupViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
) {
    val currency = settings.state.collectAsStateWithLifecycle().value.currency
    val uriHandler = LocalUriHandler.current
    // Each Money row says what's behind it; blank while the figures load
    val figures = (dashboard.state.collectAsStateWithLifecycle().value as? DashboardState.Ready)?.home
    val sums = figures?.let { moreSummary(it) { n -> formatWhole(n, currency) } }
    val snackbar = remember { SnackbarHostState() }
    val notice by plans.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        plans.noticeShown()
        snackbar.show(Toast(n.text, n.error))
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0), containerColor = SpendWiseTheme.colors.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            TabTopBar("More")
            SettingsGroup("Money") {
                SettingsRow("Income", icon = SwIcons.MoneyIn, sub = sums?.income, onClick = onIncome)
                SettingsRow("Debts", icon = SwIcons.People, sub = sums?.debts, onClick = onDebts, divider = true)
                SettingsRow("Budgets", icon = SwIcons.Receipt, sub = sums?.budget, onClick = plans::openBudget, divider = true)
                SettingsRow("Savings goals", icon = SwIcons.Target, sub = sums?.goals, onClick = onGoals, divider = true)
                SettingsRow("Investments", icon = SwIcons.TrendingUp, sub = sums?.invested, onClick = onInvestments, divider = true)
            }
            SettingsGroup("Your data") {
                SettingsRow("Import bank statement", icon = SwIcons.Upload, onClick = onImport)
                SettingsRow("Backup & restore", icon = SwIcons.Download, onClick = backup::open, divider = true)
            }
            SettingsGroup("App") {
                SettingsRow("Settings", icon = SwIcons.Settings, onClick = onSettings)
                SettingsRow("Privacy policy", icon = SwIcons.Shield, onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }, divider = true)
            }
        }
    }
    PlanDialogs(plans, currency)
    BackupDialog(backup)
}

/** The page in docs/, served by GitHub Pages. */
private const val PRIVACY_POLICY_URL = "https://shuvrasankha.github.io/spendwise-android/privacy-policy.html"
