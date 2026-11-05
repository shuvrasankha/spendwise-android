package com.spendwise.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.ai.AiError
import com.spendwise.app.data.ai.AiResult
import com.spendwise.app.data.ai.ApiKeyStore
import com.spendwise.app.data.ai.KeyUnreadableException
import com.spendwise.app.data.ai.NVIDIA_DEFAULT_MODEL
import com.spendwise.app.data.ai.NvidiaProvider
import com.spendwise.app.data.ai.byokErrorText
import com.spendwise.app.data.prefs.DevicePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val BYOK_PROVIDER = "nvidia"

sealed interface KeyTest {
    data object Idle : KeyTest
    data object Running : KeyTest
    data object Passed : KeyTest
    data class Failed(val message: String) : KeyTest
}

/** The model fields as typed, the last test, and the open dialog: what isn't in DataStore. */
data class ByokDraft(
    val loaded: Boolean = false,
    val textModel: String = "",
    val visionModel: String = "",
    val test: KeyTest = KeyTest.Idle,
    val consentAsked: Boolean = false,
    val note: String? = null,
)

data class AiProviderUi(
    val loading: Boolean = true,
    val byok: Boolean = false,
    val keyMask: String? = null,
    val textModel: String = "",
    val visionModel: String = "",
    val test: KeyTest = KeyTest.Idle,
    val consentAsked: Boolean = false,
    val note: String? = null,
) {
    val canActivate get() = canActivate(keyMask != null, test)
}

/** "My own API key" turns on only with a saved key that passed a test with the models shown. Consent is asked on top. */
fun canActivate(hasKey: Boolean, test: KeyTest) = hasKey && test == KeyTest.Passed

/** A changed model hasn't been tested. */
fun ByokDraft.withModels(text: String = textModel, vision: String = visionModel) =
    if (text == textModel && vision == visionModel) this else copy(textModel = text, visionModel = vision, test = KeyTest.Idle, note = null)

@HiltViewModel
class AiProviderViewModel @Inject constructor(
    private val prefs: DevicePrefs,
    private val keys: ApiKeyStore,
    private val nvidia: NvidiaProvider,
) : ViewModel() {
    private val draft = MutableStateFlow(ByokDraft())

    val state: StateFlow<AiProviderUi> = combine(prefs.aiMode, keys.mask(BYOK_PROVIDER), draft) { mode, mask, d ->
        AiProviderUi(!d.loaded, mode == "byok", mask, d.textModel, d.visionModel, d.test, d.consentAsked, d.note)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProviderUi())

    init {
        viewModelScope.launch {
            val text = prefs.byokModel("text", BYOK_PROVIDER).first()
            val vision = prefs.byokModel("vision", BYOK_PROVIDER).first()
            draft.update { it.copy(loaded = true, textModel = text, visionModel = vision) }
        }
    }

    fun setTextModel(model: String) = draft.update { it.withModels(text = model) }
    fun setVisionModel(model: String) = draft.update { it.withModels(vision = model) }

    /** [key] is cleared by the store; the screen clears its field. */
    fun saveKey(key: CharArray) = viewModelScope.launch {
        val note = try {
            keys.save(BYOK_PROVIDER, key)
            "Key saved. Test the connection next."
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            "That doesn't look like an API key."
        } catch (e: Exception) {
            "Key could not be stored securely."
        }
        draft.update { it.copy(test = KeyTest.Idle, note = note) }
    }

    /** One call per distinct model; the models are saved only when every call passes. */
    fun test() = viewModelScope.launch {
        val d = draft.value
        if (d.test == KeyTest.Running) return@launch
        draft.update { it.copy(test = KeyTest.Running, note = null) }
        val models = listOf(d.textModel, d.visionModel).map { it.trim().ifEmpty { NVIDIA_DEFAULT_MODEL } }.distinct()
        val result = try {
            val key = keys.read(BYOK_PROVIDER) ?: return@launch draft.update { it.copy(test = KeyTest.Failed("Save your API key first.")) }
            models.firstNotNullOfOrNull { (nvidia.testConnection(key, it) as? AiResult.Failed)?.error }
                ?.let { KeyTest.Failed(byokErrorText(it)) } ?: KeyTest.Passed
        } catch (e: CancellationException) {
            throw e
        } catch (e: KeyUnreadableException) {
            if (prefs.aiMode.first() == "byok") prefs.setAiMode("worker")
            KeyTest.Failed(byokErrorText(AiError.KeyUnreadable))
        }
        if (result == KeyTest.Passed) {
            prefs.setByokModel("text", BYOK_PROVIDER, d.textModel)
            prefs.setByokModel("vision", BYOK_PROVIDER, d.visionModel)
        }
        // A model typed while the test ran makes this result stale
        draft.update { if (it.textModel == d.textModel && it.visionModel == d.visionModel) it.copy(test = result) else it }
    }

    fun useMyKey() = viewModelScope.launch {
        val s = state.value
        if (!s.canActivate) return@launch draft.update { it.copy(note = if (s.keyMask == null) "Save your API key first." else "Test the connection first.") }
        if (prefs.byokConsent(BYOK_PROVIDER).first()) activate() else draft.update { it.copy(consentAsked = true) }
    }

    fun answerConsent(ok: Boolean) = viewModelScope.launch {
        draft.update { it.copy(consentAsked = false) }
        if (!ok) return@launch
        prefs.setByokConsent(BYOK_PROVIDER, true)
        activate()
    }

    private suspend fun activate() {
        prefs.setByokProvider(BYOK_PROVIDER)
        prefs.setAiMode("byok")
        draft.update { it.copy(note = "AI features now use your NVIDIA key.") }
    }

    /** Keeps the key, so switching back needs no new test. */
    fun useSpendWise() = viewModelScope.launch {
        prefs.setAiMode("worker")
        draft.update { it.copy(note = null) }
    }

    fun removeKey() = viewModelScope.launch {
        prefs.setAiMode("worker")
        keys.remove(BYOK_PROVIDER)
        prefs.setByokConsent(BYOK_PROVIDER, false)
        draft.update { it.copy(test = KeyTest.Idle, note = "Key removed. SpendWise AI is in use.") }
    }
}
