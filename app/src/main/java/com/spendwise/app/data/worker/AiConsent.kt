package com.spendwise.app.data.worker

import com.spendwise.app.data.prefs.DevicePrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * insights-v2.js › ensureConsent (D5): the AI is used only after "Continue", asked once per device.
 * "Not now" isn't remembered, so the next AI use asks again; until then the device answers alone.
 */
@Singleton
class AiConsent @Inject constructor(private val prefs: DevicePrefs) {
    private val _pending = MutableStateFlow<CompletableDeferred<Boolean>?>(null)
    /** Non-null while the dialog should show. */
    val pending: StateFlow<CompletableDeferred<Boolean>?> = _pending.asStateFlow()

    suspend fun ensure(): Boolean {
        if (prefs.aiConsent.first()) return true
        val asked = _pending.value ?: CompletableDeferred<Boolean>().also { _pending.value = it }
        val ok = asked.await()
        if (ok) prefs.setAiConsent()
        return ok
    }

    fun answer(ok: Boolean) {
        _pending.value?.complete(ok)
        _pending.value = null
    }
}
