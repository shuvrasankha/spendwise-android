package com.spendwise.app.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.lockExpired
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The lock for this run of the app, like app-lock.js's sessionStorage: a fresh start is locked, and so is a
 * return after 5 minutes away (spec §7.7). Only the switch is stored, on this device.
 */
@Singleton
class AppLock @Inject constructor(private val prefs: DevicePrefs, private val auth: AuthRepository) {
    private val unlocked = MutableStateFlow(false)
    private var awayAt: Long? = null

    /** True while the lock screen covers the app. */
    val locked: Flow<Boolean> = combine(prefs.appLock, unlocked) { on, open -> on && !open }

    fun markUnlocked() {
        unlocked.value = true
        awayAt = null
    }

    fun left() {
        awayAt = System.currentTimeMillis()
    }

    fun back() {
        if (lockExpired(awayAt, System.currentTimeMillis())) unlocked.value = false
        awayAt = null
    }

    /** Turning it on just passed the check, so this run stays open. */
    suspend fun setOn(on: Boolean) {
        if (on) markUnlocked()
        prefs.setAppLock(on)
    }

    /** Signing in again needs the password, so it's a safe way past a lock that won't open (app-lock.js › signOutInstead). */
    suspend fun signOutInstead() {
        prefs.setAppLock(false)
        auth.signOut()
    }
}

// Weak includes strong; strong with the screen lock isn't offered before Android 11
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** False when the phone has no fingerprint, face or screen lock set up: Settings then hides the switch. */
fun canLock(context: Context) = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** The phone asks for a fingerprint, face or screen lock; [onResult] gets true when it says yes. */
fun FragmentActivity.confirmIdentity(onResult: (Boolean) -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
    }
    val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock SpendWise").setAllowedAuthenticators(AUTHENTICATORS).build()
    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(info)
}

/** app-lock.js › showLock: asks straight away, with Unlock to ask again. */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onSignOut: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    var failed by rememberSaveable { mutableStateOf(false) }
    val ask = { activity.confirmIdentity { ok -> if (ok) onUnlocked() else failed = true } }
    LaunchedEffect(Unit) { ask() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SpendWise is locked", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Use your fingerprint, face or screen lock to open it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = ask) { Text("Unlock") }
        Text(
            if (failed) "Not unlocked. Tap Unlock to try again." else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = onSignOut) { Text("Can't unlock? Sign out") }
    }
}
