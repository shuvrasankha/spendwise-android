package com.spendwise.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.prefs.ThemeMode
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.currencyOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currency: Currency = currencyOf(null),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: DevicePrefs) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(prefs.themeMode, prefs.currency, ::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch { prefs.setCurrency(code) }
    }
}
