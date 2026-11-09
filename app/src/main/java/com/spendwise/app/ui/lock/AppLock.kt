package com.spendwise.app.ui.lock

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.PIN_FREE_TRIES
import com.spendwise.app.domain.PinFails
import com.spendwise.app.domain.lockExpired
import com.spendwise.app.domain.pinMatches
import com.spendwise.app.domain.pinRecord
import com.spendwise.app.domain.pinWaitLeft
import com.spendwise.app.domain.pinWaitMs
import com.spendwise.app.domain.validPin
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** A PIN try: right, wrong with [triesLeft] before a wait, or blocked for [ms]. */
sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val triesLeft: Int) : PinCheck
    data class Wait(val ms: Long) : PinCheck
}

/**
 * The lock for this run of the app, like app-lock.js's sessionStorage: a fresh start is locked, and so is a
 * return after 5 minutes away (spec §7.7). Everything is stored on this device only. With a PIN, the PIN opens
 * it, and fingerprint or face may stand in; a lock from before PINs uses the phone's screen lock until a PIN is set.
 */
@Singleton
class AppLock @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: DevicePrefs,
    private val auth: AuthRepository,
) {
    private val unlocked = MutableStateFlow(false)
    private val later = MutableStateFlow(false)
    private var awayAt: Long? = null

    /** True while the lock screen covers the app. */
    val locked: Flow<Boolean> = combine(prefs.appLock, unlocked) { on, open -> on && !open }

    val hasPin: Flow<Boolean> = prefs.lockPin.map { it != null }
    val biometric: Flow<Boolean> = prefs.lockBiometric

    /** A lock from before PINs, just opened: ask for a PIN once this run. */
    val needsPin: Flow<Boolean> = combine(prefs.appLock, prefs.lockPin, unlocked, later) { on, pin, open, l -> on && pin == null && open && !l }

    // Elapsed time since boot, so changing the clock doesn't open the lock early
    private fun now() = SystemClock.elapsedRealtime()
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    fun markUnlocked() {
        unlocked.value = true
        awayAt = null
    }

    fun left() {
        awayAt = now()
    }

    fun back() {
        if (lockExpired(awayAt, now())) unlocked.value = false
        awayAt = null
    }

    fun setPinLater() { later.value = true }

    /** ms before another PIN may be tried. After a restart the wait starts over from now. */
    suspend fun waitLeft(): Long {
        val f = prefs.pinFails.first()
        if (f.at != null && f.boot != boot() && pinWaitMs(f.count) > 0) prefs.setPinFails(f.copy(at = now(), boot = boot()))
        return pinWaitLeft(prefs.pinFails.first(), now(), boot())
    }

    suspend fun tryPin(pin: String): PinCheck {
        waitLeft().takeIf { it > 0 }?.let { return PinCheck.Wait(it) }
        val record = prefs.lockPin.first() ?: return PinCheck.Wrong(PIN_FREE_TRIES)
        if (withContext(Dispatchers.Default) { pinMatches(pin, record) }) {
            prefs.setPinFails(PinFails())
            markUnlocked()
            return PinCheck.Ok
        }
        val f = PinFails(prefs.pinFails.first().count + 1, now(), boot())
        prefs.setPinFails(f)
        val wait = pinWaitMs(f.count)
        return if (wait > 0) PinCheck.Wait(wait) else PinCheck.Wrong(PIN_FREE_TRIES - f.count)
    }

    /** A new PIN turns the lock on; whoever set it has just proved who they are, so this run stays open. */
    suspend fun setPin(pin: String) {
        require(validPin(pin))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.setLockPin(withContext(Dispatchers.Default) { pinRecord(pin, salt) })
        prefs.setAppLock(true)
        markUnlocked()
    }

    suspend fun setBiometric(on: Boolean) = prefs.setLockBiometric(on)

    suspend fun turnOff() = prefs.forgetLock()

    /** Forgot the PIN: signing in again needs the password, so it's a safe way past (app-lock.js › signOutInstead). */
    suspend fun signOutInstead() {
        prefs.forgetLock()
        auth.signOut()
    }
}

// Weak includes strong; strong with the screen lock isn't offered before Android 11
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Fingerprint or face is set up on this phone. */
fun canUseBiometric(context: Context) = BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

/** The phone asks for a fingerprint, face or screen lock; [onResult] gets true when it says yes. For locks from before PINs. */
fun FragmentActivity.confirmIdentity(onResult: (Boolean) -> Unit) =
    prompt(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock SpendWise").setAllowedAuthenticators(AUTHENTICATORS).build(), onResult)

/** Fingerprint or face only, with "Use PIN" to go back to the pad. */
fun FragmentActivity.confirmBiometric(onResult: (Boolean) -> Unit) =
    prompt(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock SpendWise").setAllowedAuthenticators(BIOMETRIC_WEAK).setNegativeButtonText("Use PIN").build(), onResult)

private fun FragmentActivity.prompt(info: BiometricPrompt.PromptInfo, onResult: (Boolean) -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
    }
    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(info)
}

/** The activity behind a Compose context, which can be a wrapper around it. */
fun Context.findActivity(): FragmentActivity {
    var c = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    error("No FragmentActivity behind this context")
}

/** The lock screen: the PIN pad (with fingerprint or face when on), or the screen-lock prompt for a lock from before PINs. */
@Composable
fun LockScreen(lock: AppLock, onSignOut: () -> Unit) {
    val hasPin by lock.hasPin.collectAsStateWithLifecycle(null)
    when (hasPin) {
        null -> Unit
        true -> PinLockScreen(lock, onSignOut)
        false -> ScreenLockScreen(lock::markUnlocked, onSignOut)
    }
}

@Composable
private fun PinLockScreen(lock: AppLock, onSignOut: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val bioOn by lock.biometric.collectAsStateWithLifecycle(false)
    val bio = bioOn && remember { canUseBiometric(activity) }
    var forgot by rememberSaveable { mutableStateOf(false) }
    val askBio = { activity.confirmBiometric { ok -> if (ok) lock.markUnlocked() } }
    LaunchedEffect(bio) { if (bio) askBio() }
    PinCheckPad(
        title = "Enter your PIN",
        tryPin = lock::tryPin,
        waitLeft = lock::waitLeft,
        onOk = {},
        below = {
            if (bio) SwButton("Use fingerprint or face", askBio, style = ButtonStyle.Text, compact = true)
            SwButton("Forgot PIN?", { forgot = true }, style = ButtonStyle.Text, compact = true)
        },
    )
    if (forgot) AlertDialog(
        onDismissRequest = { forgot = false },
        title = { Text("Sign out to reset your PIN?") },
        text = { Text("Your entries are saved to your account. Sign in again, then set a new PIN. Anything this phone hasn't synced yet is lost.") },
        confirmButton = { TextButton({ forgot = false; onSignOut() }) { Text("Sign out") } },
        dismissButton = { TextButton({ forgot = false }) { Text("Cancel") } },
    )
}

/** app-lock.js › showLock: asks straight away, with Unlock to ask again. */
@Composable
private fun ScreenLockScreen(onUnlocked: () -> Unit, onSignOut: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val c = SpendWiseTheme.colors
    var failed by rememberSaveable { mutableStateOf(false) }
    val ask = { activity.confirmIdentity { ok -> if (ok) onUnlocked() else failed = true } }
    LaunchedEffect(Unit) { ask() }
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SpendWise is locked", style = SwType.title, color = c.text)
        Text("Use your fingerprint, face or screen lock to open it.", style = SwType.body, color = c.textMuted, textAlign = TextAlign.Center)
        SwButton("Unlock", ask)
        Text(if (failed) "Not unlocked. Tap Unlock to try again." else "", style = SwType.label, color = c.textMuted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        SwButton("Can't unlock? Sign out", onSignOut, style = ButtonStyle.Text, compact = true)
    }
}

/** A pad that checks each PIN with [tryPin], says how many tries are left, and counts down a wait. */
@Composable
fun PinCheckPad(
    title: String,
    tryPin: suspend (String) -> PinCheck,
    waitLeft: suspend () -> Long,
    onOk: () -> Unit,
    onCancel: (() -> Unit)? = null,
    below: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { wait = waitLeft() }
    LaunchedEffect(wait > 0) {
        while (wait > 0) {
            message = "Too many tries. Try again in ${(wait + 999) / 1000} s."
            delay(1000)
            wait = (wait - 1000).coerceAtLeast(0)
            if (wait == 0L) message = null
        }
    }
    PinPad(title, message, enabled = wait == 0L && !busy, onCancel = onCancel, below = below) { pin, clear ->
        busy = true
        scope.launch {
            when (val r = tryPin(pin)) {
                PinCheck.Ok -> onOk()
                is PinCheck.Wrong -> message = "Wrong PIN. ${r.triesLeft} ${if (r.triesLeft == 1) "try" else "tries"} left before a wait."
                is PinCheck.Wait -> wait = r.ms
            }
            clear()
            busy = false
        }
    }
}

/** Choose a PIN, then type it again. [onPin] gets it once both match. */
@Composable
fun SetPinPad(title: String, onPin: (String) -> Unit, cancelLabel: String = "Cancel", onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>("4 to 6 digits. You'll need it each time SpendWise opens.") }
    PinPad(if (first == null) title else "Type it again", message, onCancel = onCancel, cancelLabel = cancelLabel) { pin, clear ->
        val f = first
        when {
            f == null -> { first = pin; message = null }
            f == pin -> onPin(pin)
            else -> { first = null; message = "The PINs didn't match. Choose one again." }
        }
        clear()
    }
}

/** Dots for what's typed, a number pad, and OK once 4 to 6 digits are in. Full screen, on the page's background. */
@Composable
private fun PinPad(
    title: String,
    message: String?,
    enabled: Boolean = true,
    onCancel: (() -> Unit)? = null,
    cancelLabel: String = "Cancel",
    below: @Composable () -> Unit = {},
    onSubmit: (pin: String, clear: () -> Unit) -> Unit,
) {
    val c = SpendWiseTheme.colors
    var pin by remember { mutableStateOf("") }
    val clear = { pin = "" }
    Column(
        Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(SwIcons.Lock, contentDescription = null, tint = c.text, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = SwType.title, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.height(16.dp).clearAndSetSemantics { contentDescription = "${pin.length} of up to 6 digits typed" },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(maxOf(pin.length, 4)) { i -> Box(Modifier.size(12.dp).background(if (i < pin.length) c.text else c.line, Radius.pill)) }
        }
        Spacer(Modifier.height(16.dp))
        Text(message.orEmpty(), style = SwType.label, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(24.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Box(Modifier.weight(1f)) {
                            when (k) {
                                "" -> Unit
                                "⌫" -> SwButton("Delete", { pin = pin.dropLast(1) }, Modifier.fillMaxWidth(), style = ButtonStyle.Text, enabled = enabled && pin.isNotEmpty())
                                else -> SwButton(k, { if (pin.length < 6) pin += k }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = enabled)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SwButton("OK", { onSubmit(pin, clear) }, Modifier.widthIn(max = 320.dp).fillMaxWidth(), enabled = enabled && validPin(pin))
        Spacer(Modifier.height(8.dp))
        below()
        if (onCancel != null) SwButton(cancelLabel, onCancel, style = ButtonStyle.Text, compact = true)
    }
}
