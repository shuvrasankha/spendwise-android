package com.spendwise.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.BackupRepository
import com.spendwise.app.data.export.ExportFiles
import com.spendwise.app.domain.RestorePlan
import com.spendwise.app.domain.backupFileName
import com.spendwise.app.domain.backupItemCount
import com.spendwise.app.domain.backupJson
import com.spendwise.app.domain.restorePlan
import com.spendwise.app.domain.todayStr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** The backup dialog's status line, worded as backup.js › showBackupStatus. */
data class BackupStatus(val text: String, val error: Boolean = false)

data class BackupState(
    val open: Boolean = false,
    val busy: Boolean = false,
    val status: BackupStatus? = null,
    /** The file picked to restore, and its name; Restore is on once there is one. */
    val picked: Uri? = null,
    val pickedName: String? = null,
)

/** Settings › Backup & restore (spec §7.8, backup.js). */
@HiltViewModel
class BackupViewModel @Inject constructor(private val repo: BackupRepository, private val files: ExportFiles) : ViewModel() {

    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    val fileName: String get() = backupFileName(todayStr())

    fun open() = _state.update { BackupState(open = true) }
    fun close() = _state.update { if (it.busy) it else BackupState() }

    private fun status(text: String, error: Boolean = false) = _state.update { it.copy(status = BackupStatus(text, error)) }

    /** [uri]: where the user chose to save it (CreateDocument); null when they cancelled. */
    fun export(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            status("Fetching your data…")
            try {
                val d = repo.read()
                val exportDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(Instant.now())
                files.save(uri, backupJson(exportDate, d.expenses, d.income, d.debts, d.holdings))
                status("Exported ${backupItemCount(d.expenses, d.income, d.debts, d.holdings)} items successfully!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status("Export failed. Please try again.", error = true)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun pick(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            val name = runCatching { files.name(uri) }.getOrNull()
            _state.update { it.copy(picked = uri, pickedName = name ?: "Backup file", status = null) }
        }
    }

    fun restore() {
        val uri = _state.value.picked ?: return status("Please select a backup file first.", error = true)
        // Once started it finishes even if the dialog closes: a half-written restore is only fixed by a retry
        viewModelScope.launch { withContext(NonCancellable) {
            _state.update { it.copy(busy = true) }
            status("Reading file…")
            try {
                val plan = restorePlan(files.read(uri), repo.uid)
                status("Preparing import…")
                when (plan) {
                    RestorePlan.Invalid -> status("Invalid backup file. Please use a valid SpendWise backup.", error = true)
                    RestorePlan.Broken -> status("Import failed. Please try again.", error = true)
                    is RestorePlan.Ok -> if (plan.writes.isEmpty()) {
                        status("No items found for your account in this backup.", error = true)
                    } else {
                        repo.restore(plan.writes) { done, of -> status("Importing $done of $of items…") }
                        status("Imported ${plan.writes.size} items successfully!")
                    }
                }
            } catch (e: Exception) {
                status("Import failed. Please try again.", error = true)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        } }
    }
}
