package com.spendwise.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.prefs.ThemeMode
import com.spendwise.app.ui.auth.AuthLoading
import com.spendwise.app.ui.auth.SignInScreen
import com.spendwise.app.ui.lock.AppLock
import com.spendwise.app.ui.lock.LockScreen
import com.spendwise.app.ui.navigation.AppShell
import com.spendwise.app.ui.navigation.ShareInbox
import com.spendwise.app.ui.navigation.sharedText
import com.spendwise.app.ui.navigation.linkRoute
import com.spendwise.app.ui.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
// A FragmentActivity so BiometricPrompt can show over it
class MainActivity : FragmentActivity() {

    @Inject lateinit var prefs: DevicePrefs
    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var inbox: ShareInbox
    @Inject lateinit var lock: AppLock

    /** A link waiting to open; it survives sign-in and is cleared once the shell has opened it. */
    private var link by mutableStateOf<Any?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh start: after a rotation the link has already been opened
        if (savedInstanceState == null) link = intent.route()
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
            val locked by lock.locked.collectAsStateWithLifecycle(null)
            val scope = rememberCoroutineScope()
            SpendWiseTheme(themeMode) {
                // Auth gate: the app shell only exists for a signed-in, verified user (spec §1.0)
                Surface(Modifier.fillMaxSize()) {
                    when {
                        authState == AuthState.Loading || locked == null -> AuthLoading()
                        authState == AuthState.SignedOut -> SignInScreen()
                        locked == true -> LockScreen(lock::markUnlocked, onSignOut = { scope.launch { lock.signOutInstead() } })
                        else -> AppShell(link, onLinkOpened = { link = null })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.back()
    }

    override fun onStop() {
        super.onStop()
        // A rotation isn't time away
        if (!isChangingConfigurations) lock.left()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.route()?.let { link = it }
    }

    private fun Intent.route(): Any? = sharedText()?.let(inbox::receive) ?: linkRoute()
}
