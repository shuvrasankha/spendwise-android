package com.spendwise.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.EntryBatch
import com.spendwise.app.data.SyncStatus
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.worker.WorkerClient
import com.spendwise.app.data.worker.aiContent
import com.spendwise.app.data.worker.aiRequestOf
import com.spendwise.app.data.ReceiptPhotos
import android.net.Uri
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import com.spendwise.app.domain.RECEIPT_AI_MODEL
import com.spendwise.app.domain.RECEIPT_BAD_PHOTO
import com.spendwise.app.domain.RECEIPT_BUSY
import com.spendwise.app.domain.RECEIPT_NONE
import com.spendwise.app.domain.receiptEntries
import com.spendwise.app.domain.receiptMessages
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.VOICE_AI_MODEL
import com.spendwise.app.domain.VOICE_BUSY
import com.spendwise.app.domain.VOICE_EMPTY
import com.spendwise.app.domain.VOICE_FAILED
import com.spendwise.app.domain.VOICE_FROM_SMS
import com.spendwise.app.domain.VOICE_LANGUAGES
import com.spendwise.app.domain.VOICE_MODEL_LOADING
import com.spendwise.app.domain.VOICE_NONE
import com.spendwise.app.domain.VOICE_OFFLINE
import com.spendwise.app.domain.VOICE_RATE_LIMITED
import com.spendwise.app.domain.VOICE_SAVE_FAILED
import com.spendwise.app.domain.VoiceEntry
import com.spendwise.app.domain.VoiceLanguage
import com.spendwise.app.domain.cleanEntry
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.emiCount
import com.spendwise.app.domain.foundText
import com.spendwise.app.domain.parseSms
import com.spendwise.app.domain.parseTags
import com.spendwise.app.domain.readEntries
import com.spendwise.app.domain.reviewProblem
import com.spendwise.app.domain.todayStr
import com.spendwise.app.domain.voiceAmount
import com.spendwise.app.domain.voiceEntryFrom
import com.spendwise.app.domain.voicePrompt
import com.spendwise.app.domain.voiceSaved
import com.spendwise.app.ui.format.formatMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

data class VoiceState(
    val language: VoiceLanguage = VOICE_LANGUAGES.first(),
    val text: String = "",
    val entries: List<VoiceEntry> = emptyList(),
    /** One document id per entry, kept across retries so a retry overwrites instead of duplicating (refFor). */
    val ids: List<String> = emptyList(),
    val message: FormMessage? = null,
    val reading: Boolean = false,
    /** Sharing a debt needs a verified email (firestore.rules › validShare). */
    val canShare: Boolean = false,
)

/** Speak or type entries (spec §7.4): read on the device (a bank SMS) or by the AI, reviewed, then saved together. */
@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val worker: WorkerClient,
    private val batch: EntryBatch,
    private val auth: AuthRepository,
    private val photos: ReceiptPhotos,
    sync: SyncStatus,
    prefs: DevicePrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(VoiceState(canShare = auth.currentUser?.emailVerified == true))
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    val currency: StateFlow<Currency> = prefs.currency.stateIn(viewModelScope, SharingStarted.Eagerly, currencyOf(null))
    private val online = sync.online.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setLanguage(l: VoiceLanguage) = _state.update { it.copy(language = l) }
    fun setText(t: String) = _state.update { it.copy(text = t) }
    fun offlineForMic(): Boolean = (!online.value).also { if (it) _state.update { s -> s.copy(message = FormMessage(VOICE_OFFLINE)) } }

    /** handleTextSubmit: a bank SMS is read here, anything else goes to the AI. */
    fun submit() {
        val text = _state.value.text.trim()
        if (text.isEmpty()) return _state.update { it.copy(message = FormMessage(VOICE_EMPTY)) }
        parseSms(text, LocalDate.now())?.let { sms -> return show(listOf(voiceEntryFrom(sms)), VOICE_FROM_SMS) }
        if (!online.value) return _state.update { it.copy(message = FormMessage(VOICE_OFFLINE)) }
        val today = LocalDate.now()
        val messages = listOf("system" to voicePrompt(today, _state.value.language), "user" to text)
        viewModelScope.launch { read(VOICE_AI_MODEL, messages.map { (r, c) -> r to JsonPrimitive(c) }, VOICE_BUSY, VOICE_NONE) { it } }
    }

    /** Where the camera saves the next receipt photo. */
    fun newPhotoUri(): Uri = photos.newUri()

    /** scanReceipt: the total, date, shop and category off a photo; a photo the camera took is deleted once read. */
    fun scanReceipt(uri: Uri?) {
        if (uri == null) return photos.clear()
        if (!online.value) {
            photos.clear()
            return _state.update { it.copy(entries = emptyList(), ids = emptyList(), message = FormMessage(VOICE_OFFLINE)) }
        }
        _state.update { it.copy(entries = emptyList(), ids = emptyList(), reading = true, message = FormMessage(RECEIPT_BUSY, success = true)) }
        viewModelScope.launch {
            val image = try {
                photos.dataUrl(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                photos.clear()
            }
            if (image == null) return@launch _state.update { it.copy(reading = false, message = FormMessage(RECEIPT_BAD_PHOTO)) }
            val today = todayStr()
            read(RECEIPT_AI_MODEL, receiptMessages(today, image), RECEIPT_BUSY, RECEIPT_NONE) { receiptEntries(it, today) }
        }
    }

    /** parseWithAI: sends the messages and opens the review cards with what comes back; nothing is saved yet. */
    private suspend fun read(model: String, messages: List<Pair<String, JsonElement>>, busy: String, none: String, fix: (List<VoiceEntry>) -> List<VoiceEntry>) {
        _state.update { it.copy(reading = true, message = FormMessage(busy, success = true)) }
        val failure = try {
            val res = worker.post("/", aiRequestOf(model, messages, maxTokens = 1500, temperature = 0.1, topP = 0.95))
            when {
                res.status == 429 -> VOICE_RATE_LIMITED
                res.status == 503 -> VOICE_MODEL_LOADING
                else -> {
                    val content = (if (res.ok) aiContent(res.body) else null) ?: throw IOException("AI ${res.status}")
                    val entries = fix(readEntries(content).mapNotNull { cleanEntry(it, todayStr()) })
                    if (entries.isEmpty()) none else return show(entries, foundText(entries.size))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VOICE_FAILED
        }
        _state.update { it.copy(reading = false, message = FormMessage(failure)) }
    }

    private fun show(entries: List<VoiceEntry>, status: String) {
        // Without a verified email the share would be refused, so the review never offers one
        val shown = if (_state.value.canShare) entries else entries.map { it.copy(email = "") }
        _state.update { it.copy(entries = shown, ids = entries.map { batch.newId() }, reading = false, message = FormMessage(status, success = true)) }
    }

    /** onPreviewInput: one field of entry [i] changed. */
    fun edit(i: Int, change: (VoiceEntry) -> VoiceEntry) =
        _state.update { s -> s.copy(entries = s.entries.mapIndexed { j, e -> if (j == i) change(e) else e }, message = null) }

    fun setAmount(i: Int, text: String) = edit(i) { it.copy(amountText = text, amount = voiceAmount(text)) }
    fun setTags(i: Int, text: String) = edit(i) { it.copy(tags = parseTags(text)) }
    fun setEmi(i: Int, text: String) = edit(i) { it.copy(emiRaw = text.trim(), emiTotal = emiCount(text)) }
    fun setEmail(i: Int, text: String) = edit(i) { it.copy(email = text.trim().lowercase()) }

    fun remove(i: Int) {
        _state.update { s -> s.copy(entries = s.entries.filterIndexed { j, _ -> j != i }, ids = s.ids.filterIndexed { j, _ -> j != i }, message = null) }
    }

    fun save() {
        val s = _state.value
        if (s.entries.isEmpty()) return
        reviewProblem(s.entries, auth.currentUser?.email.orEmpty())?.let { problem -> return _state.update { it.copy(message = FormMessage(problem)) } }
        val what = voiceSaved(s.entries, ::money)
        val write = batch.save(s.entries, s.ids)
        val done = if (online.value) "$what!" else "You're offline: $what on this device. They sync when you're back online."
        _state.value = VoiceState(language = s.language, canShare = s.canShare, message = FormMessage(done, success = true))
        viewModelScope.launch {
            try {
                write.confirmed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The server refused the batch, so none of it is kept: bring the entries back, with the same ids for the retry
                _state.update { it.copy(entries = s.entries, ids = s.ids, message = FormMessage(VOICE_SAVE_FAILED)) }
            }
        }
    }

    private fun money(n: Double) = formatMoney(n, currency.value)
}
