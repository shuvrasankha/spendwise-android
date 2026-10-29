package com.spendwise.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.DebtRepository
import com.spendwise.app.data.HoldingRepository
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.SettingsRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.HomeFigures
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.Period
import com.spendwise.app.domain.PeriodSummary
import com.spendwise.app.domain.TimeTab
import com.spendwise.app.domain.Trend
import com.spendwise.app.domain.UserSettings
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.homeFigures
import com.spendwise.app.domain.periodSummary
import com.spendwise.app.domain.tableRows
import com.spendwise.app.domain.trend
import com.spendwise.app.domain.yearOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/** "Spending over time" controls. The web starts on Weekly with this month and year. */
data class TimeView(val tab: TimeTab = TimeTab.WEEKLY, val period: Period = Period.current(LocalDate.now()))


data class OverTime(
    val view: TimeView,
    val summary: PeriodSummary,
    val trend: Trend,
    val rows: List<Expense>,
    val years: List<String>,
)

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Failed(val message: String) : DashboardState
    /** [firstTime]: no expenses at all, so the Today card points at the first entry. */
    data class Ready(val home: HomeFigures, val firstTime: Boolean, val overTime: OverTime) : DashboardState
}

private data class Sources(
    val expenses: List<Expense>,
    val income: List<Income>,
    val debts: List<Debt>,
    val settings: UserSettings,
    val holdings: List<Holding>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    expenseRepo: ExpenseRepository,
    incomeRepo: IncomeRepository,
    debtRepo: DebtRepository,
    settingsRepo: SettingsRepository,
    holdingRepo: HoldingRepository,
    prefs: DevicePrefs,
    auth: AuthRepository,
) : ViewModel() {

    val name: String = auth.currentUser?.displayName.orEmpty()

    private val reload = MutableStateFlow(0)
    private val view = MutableStateFlow(TimeView())

    val state: StateFlow<DashboardState> = reload.flatMapLatest {
        // Only an expenses failure is shown; the web just logs income, debt and settings load errors
        val sources = combine(
            expenseRepo.expenses(),
            incomeRepo.income().catch { emit(emptyList()) },
            debtRepo.debts().catch { emit(emptyList()) },
            settingsRepo.settings().catch { emit(UserSettings()) },
            holdingRepo.holdings().catch { emit(emptyList()) },
            ::Sources,
        )
        combine(sources, view, prefs.skippedBills) { s, v, skipped -> ready(s, v, skipped) }
            .flowOn(Dispatchers.Default)
            .onStart { emit(DashboardState.Loading) }
            .catch { emit(DashboardState.Failed("Error loading data. Check your connection and try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState.Loading)

    val currency: StateFlow<Currency> = prefs.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currencyOf(null))

    fun retry() = reload.update { it + 1 }

    // Any change to the tab or period starts the table over at 10 rows, as on the web
    fun selectTab(tab: TimeTab) = view.update { it.copy(tab = tab) }
    fun selectMonth(month: String?) = view.update { it.copy(period = it.period.copy(month = month)) }
    fun selectYear(year: String?) = view.update { it.copy(period = it.period.copy(year = year)) }

    private fun ready(s: Sources, v: TimeView, skipped: Set<String>): DashboardState {
        val today = LocalDate.now()
        return DashboardState.Ready(
            home = homeFigures(s.expenses, s.income, s.debts, s.settings, s.holdings, today) { key, month -> "$key:$month" in skipped },
            firstTime = s.expenses.isEmpty(),
            overTime = OverTime(
                view = v,
                summary = periodSummary(s.expenses, v.period),
                trend = trend(s.expenses, v.tab, v.period, today),
                rows = tableRows(s.expenses, v.tab, v.period, today),
                years = yearOptions(s.expenses, today),
            ),
        )
    }
}
