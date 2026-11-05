package com.spendwise.app.data.ai

import com.spendwise.app.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every AI feature calls this, never a provider (BYOK plan §4.1, §7). Worker mode is unchanged. BYOK
 * mode needs the provider consent and a readable key, and never falls back to the Worker: the user
 * chose where their data goes. The features keep their own consent and the Worker's quota.
 */
@Singleton
class AiGateway @Inject constructor(
    private val prefs: DevicePrefs,
    private val keys: ApiKeyStore,
    private val worker: WorkerAiProvider,
    private val byokClient: ByokClient,
) {
    /** Whether AI goes to the user's own provider; the Worker's daily allowance applies only when false. */
    suspend fun byok(): Boolean = prefs.aiMode.first() == "byok"

    suspend fun complete(call: AiCall): AiResult {
        // Read once: a call in progress keeps its provider if the mode changes
        if (!byok()) return worker.complete(workerModel(call.task), call)
        val p = byokProvider(prefs.byokProvider.first())
        val provider = p.id
        if (!prefs.byokConsent(provider).first()) return AiResult.Failed(AiError.NoConsent)
        val key = try {
            keys.read(provider)
        } catch (e: KeyUnreadableException) {
            null
        }
        if (key == null) {
            prefs.setAiMode("worker") // said in byokErrorText(KeyUnreadable), so the switch isn't silent
            return AiResult.Failed(AiError.KeyUnreadable)
        }
        val kind = if (call.task.needsVision) "vision" else "text"
        val model = prefs.byokModel(kind, provider).first().ifBlank { p.defaultModel }
        return byokClient.complete(p, key, model, call)
    }
}

/** A BYOK failure carried out of a feature's call, so the feature can show [byokErrorText]. */
class AiFailedException(val error: AiError) : Exception(error.toString())
