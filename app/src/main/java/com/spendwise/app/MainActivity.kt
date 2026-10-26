package com.spendwise.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.prefs.ThemeMode
import com.spendwise.app.ui.auth.AuthLoading
import com.spendwise.app.ui.auth.SignInScreen
import com.spendwise.app.ui.navigation.AppShell
import com.spendwise.app.ui.navigation.linkRoute
import com.spendwise.app.ui.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: DevicePrefs
    @Inject lateinit var auth: AuthRepository

    /** A link waiting to open; it survives sign-in and is cleared once the shell has opened it. */
    private var link by mutableStateOf<Any?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh start: after a rotation the link has already been opened
        if (savedInstanceState == null) link = intent.linkRoute()
        enableEdgeToEdge()
        setContent {
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System bar icons follow the app's theme, which can differ from the system's
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val authState by auth.authState.collectAsStateWithLifecycle(AuthState.Loading)
            SpendWiseTheme(themeMode) {
                // Auth gate: the app shell only exists for a signed-in, verified user (spec §1.0)
                Surface(Modifier.fillMaxSize()) {
                    when (authState) {
                        AuthState.Loading -> AuthLoading()
                        AuthState.SignedOut -> SignInScreen()
                        is AuthState.SignedIn -> AppShell(link) { link = null }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.linkRoute()?.let { link = it }
    }
}
