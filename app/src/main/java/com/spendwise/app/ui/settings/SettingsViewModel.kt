package com.spendwise.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
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
    val name: String = "",
    val email: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: DevicePrefs,
    private val auth: AuthRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(prefs.themeMode, prefs.currency, auth.authState) { mode, currency, authState ->
        val user = (authState as? AuthState.SignedIn)?.user
        SettingsUiState(mode, currency, user?.displayName.orEmpty(), user?.email.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch { prefs.setCurrency(code) }
    }

    /** The auth gate shows the sign-in screen once this lands. */
    fun logOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
