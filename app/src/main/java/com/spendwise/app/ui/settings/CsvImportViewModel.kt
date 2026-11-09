package com.spendwise.app.ui.settings

import com.spendwise.app.data.worker.AiAllowance
import com.spendwise.app.domain.AiFeature
import com.spendwise.app.domain.aiLimitText
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.CsvImporter
import com.spendwise.app.data.StatementPasswordException
import com.spendwise.app.data.StatementReader
import com.spendwise.app.data.SyncStatus
import com.spendwise.app.domain.PDF_MAX_LINES
import com.spendwise.app.domain.StatementKind
import com.spendwise.app.domain.fromHeaderRow
import com.spendwise.app.data.export.ExportFiles
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.data.ai.AiCall
import com.spendwise.app.data.ai.AiFailedException
import com.spendwise.app.data.ai.AiError
import com.spendwise.app.data.ai.AiGateway
import com.spendwise.app.data.ai.AiResult
import com.spendwise.app.data.ai.AiTask
import com.spendwise.app.data.ai.byokErrorText
import com.spendwise.app.data.ai.textMessages
import com.spendwise.app.domain.CSV_MAX_BYTES
import com.spendwise.app.domain.CSV_READ_TIMEOUT_MS
import com.spendwise.app.domain.CSV_SAVE_FAILED
import com.spendwise.app.domain.CSV_TOO_BIG
import com.spendwise.app.domain.CsvImportError
import com.spendwise.app.domain.CsvTx
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.checkCsvLines
import com.spendwise.app.domain.csvChunks
import com.spendwise.app.domain.csvDoneText
import com.spendwise.app.domain.csvErrorText
import com.spendwise.app.domain.csvPrompt
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.mapInParallel
import com.spendwise.app.domain.readCsvReply
import com.spendwise.app.domain.todayStr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** csv-upload.js's phases: upload → processing → preview → saving → done, or error. */
sealed interface CsvPhase {
    /** [needsPassword]: the picked PDF is locked, so a password field shows. */
    data class Upload(val fileName: String? = null, val note: String? = null, val needsPassword: Boolean = false) : CsvPhase
    data class Processing(val step: String) : CsvPhase
    data class Preview(val rows: List<CsvTx>, val ids: List<String>, val selected: Set<Int>, val note: String? = null) : CsvPhase
    data object Saving : CsvPhase
    data class Done(val text: String) : CsvPhase
    data class Failed(val text: String) : CsvPhase
}

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val files: ExportFiles,
    private val reader: StatementReader,
    private val ai: AiGateway,
    private val importer: CsvImporter,
    private val prefs: DevicePrefs,
    private val allowance: AiAllowance,
    sync: SyncStatus,
) : ViewModel() {
    private val _phase = MutableStateFlow<CsvPhase>(CsvPhase.Upload())
    val phase: StateFlow<CsvPhase> = _phase.asStateFlow()
    private val _consentAsked = MutableStateFlow(false)
    val consentAsked: StateFlow<Boolean> = _consentAsked.asStateFlow()
    val currency: StateFlow<Currency> = prefs.currency.stateIn(viewModelScope, SharingStarted.Eagerly, currencyOf(null))
    private val online = sync.online.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private var file: Uri? = null
    /** A locked PDF's password, for this file only; never saved. */
    private var password: String? = null

    fun pick(uri: Uri?) {
        uri ?: return
        file = uri
        password = null
        viewModelScope.launch { _phase.value = CsvPhase.Upload(files.name(uri) ?: "Statement") }
    }

    /** handleFileUpload: asks once per device before the file goes to the AI. */
    fun parse(password: String? = null) {
        if (file == null) return _phase.update { CsvPhase.Upload(note = "Please select a file.") }
        if (password != null) this.password = password
        viewModelScope.launch { if (prefs.csvConsent.first()) process() else _consentAsked.value = true }
    }

    fun answerConsent(ok: Boolean) {
        _consentAsked.value = false
        if (ok) viewModelScope.launch { prefs.setCsvConsent(); process() }
    }

    private suspend fun process() {
        val uri = file ?: return
        val byok = ai.byok()
        if (!byok && !allowance.left(AiFeature.CSV)) return _phase.update { CsvPhase.Failed(aiLimitText(AiFeature.CSV)) }
        _phase.value = CsvPhase.Processing("Reading your statement…")
        _phase.value = try {
            val (kind, raw) = reader.read(uri, password)
            // A PDF's text isn't CSV: a stray quote would join its lines into one record
            val text = fromHeaderRow(if (kind == StatementKind.PDF) raw.replace('"', '\'') else raw)
            if (text.toByteArray().size > CSV_MAX_BYTES) throw CsvImportError(CSV_TOO_BIG)
            if (kind == StatementKind.PDF) checkCsvLines(text, PDF_MAX_LINES, "PDF_TOO_LONG") else checkCsvLines(text)
            _phase.value = CsvPhase.Processing("AI is analyzing your data…")
            // In parallel, in file order; one failed piece fails the whole file, as one call would.
            // Two at a time on the user's own key, whose free tier may refuse a burst (BYOK plan §7.3).
            val chunks = csvChunks(text)
            val rows = mapInParallel(chunks, if (byok) 2 else chunks.size) { extract(it, byok) }.flatten()
            if (!byok) allowance.used(AiFeature.CSV)
            if (rows.isEmpty()) throw CsvImportError("NO_DATA_EXTRACTED")
            CsvPhase.Preview(rows, rows.map { importer.newId() }, rows.indices.toSet())
        } catch (e: CancellationException) {
            throw e
        } catch (e: StatementPasswordException) {
            CsvPhase.Upload(files.name(uri) ?: "Statement", if (e.wrong) "That password didn't open it. Try again." else null, needsPassword = true)
        } catch (e: AiFailedException) {
            CsvPhase.Failed(byokErrorText(e.error))
        } catch (e: CsvImportError) {
            CsvPhase.Failed(csvErrorText(e.code))
        } catch (e: IOException) {
            CsvPhase.Failed(csvErrorText("Couldn't reach the AI. Check your connection."))
        } catch (e: Exception) {
            CsvPhase.Failed(csvErrorText(e.message ?: "API_ERROR"))
        }
    }

    /** callAIModel for one piece of the file. */
    private suspend fun extract(csv: String, byok: Boolean): List<CsvTx> {
        val (system, user) = csvPrompt(csv)
        val res = ai.complete(AiCall(AiTask.CSV, textMessages(listOf("system" to system, "user" to user)), maxTokens = 4096, temperature = 0.1, topP = 0.9, readTimeoutMs = CSV_READ_TIMEOUT_MS))
        if (res is AiResult.Failed) {
            if (byok) throw AiFailedException(res.error)
            if (res.status == 429) throw CsvImportError("RATE_LIMITED")
            if (res.status == 503) throw CsvImportError("MODEL_LOADING")
            if (res.error == AiError.Network) throw IOException("AI unreachable")
            if (res.error != AiError.BadReply) throw CsvImportError("API_ERROR") // a reply without content reads as EMPTY_RESPONSE below
        }
        return readCsvReply((res as? AiResult.Ok)?.content, todayStr())
    }

    fun toggle(i: Int) = _phase.update { p ->
        if (p is CsvPhase.Preview) p.copy(selected = if (i in p.selected) p.selected - i else p.selected + i, note = null) else p
    }

    /** The AI got the direction wrong: money in becomes an expense, or the reverse. */
    fun flipType(i: Int) = _phase.update { p ->
        if (p is CsvPhase.Preview) p.copy(rows = p.rows.mapIndexed { n, tx -> if (n == i) tx.copy(type = if (tx.type == "income") "expense" else "income") else tx }) else p
    }

    fun toggleAll(on: Boolean) = _phase.update { p ->
        if (p is CsvPhase.Preview) p.copy(selected = if (on) p.rows.indices.toSet() else emptySet(), note = null) else p
    }

    /** handleConfirmImport: waits for the server, as the web does, so it needs a connection. */
    fun import() {
        val p = _phase.value as? CsvPhase.Preview ?: return
        val picked = p.rows.indices.filter { it in p.selected }
        if (picked.isEmpty()) return _phase.update { p.copy(note = "No transactions selected.") }
        if (!online.value) return _phase.update { CsvPhase.Failed(CSV_SAVE_FAILED) }
        _phase.value = CsvPhase.Saving
        viewModelScope.launch {
            _phase.value = try {
                val (expenses, income) = importer.save(picked.map { p.rows[it] }, picked.map { p.ids[it] })
                CsvPhase.Done(csvDoneText(expenses, income))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CsvPhase.Failed(CSV_SAVE_FAILED)
            }
        }
    }

    /** Retry and Back both start over at the upload step, keeping the picked file. */
    fun restart() = viewModelScope.launch { _phase.value = CsvPhase.Upload(file?.let { files.name(it) ?: "Statement" }, needsPassword = password != null) }
}
