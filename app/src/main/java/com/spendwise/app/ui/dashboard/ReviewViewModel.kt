package com.spendwise.app.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.IncomeRepository
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.IncomeCheck
import com.spendwise.app.domain.ReviewItem
import com.spendwise.app.domain.queued
import com.spendwise.app.domain.toIncome
import com.spendwise.app.domain.toInput
import com.spendwise.app.reminder.Notifications
import com.spendwise.app.ui.navigation.ShareInbox
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home's "To review": bank SMS payments and money in read on this device, saved only once approved. Each save
 * also remembers the payee's category and description for its next SMS (domain › AutoLog).
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: DevicePrefs,
    private val expenses: ExpenseRepository,
    private val incomeRepo: IncomeRepository,
    private val inbox: ShareInbox,
) : ViewModel() {

    // An emptied list takes its notification with it
    val items: StateFlow<List<ReviewItem>> = prefs.smsReview
        .onEach { if (it.isEmpty()) Notifications.reviewDone(context) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** For "Possibly already added" on money in; a load error just leaves the hint out. */
    val income: StateFlow<List<Income>> = incomeRepo.income().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun errorShown() {
        _error.value = null
    }

    /**
     * Saves each with its description. Money in that needs a bank stays, for Edit. One the server refuses goes
     * back on the list, so nothing is lost.
     */
    fun approve(items: List<Pair<ReviewItem, String>>) = viewModelScope.launch {
        for ((item, description) in items) {
            val write: PendingWrite
            val saved: String
            try {
                if (item.income) {
                    val check = item.toIncome(description) as? IncomeCheck.Ok ?: continue
                    write = incomeRepo.add(check.input)
                    saved = check.input.source
                } else {
                    val input = item.toInput(description)
                    write = expenses.add(input)
                    saved = input.description
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = FAILED
                continue
            }
            prefs.finishReview(item, item.category, saved)
            // Offline, a write waits here until it reaches the server; the entry already shows meanwhile
            launch {
                try {
                    write.confirmed()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    prefs.updateSmsReview { queued(it, item) }
                    _error.value = FAILED
                }
            }
        }
    }

    fun setCategory(item: ReviewItem, category: String) = viewModelScope.launch {
        prefs.updateSmsReview { list -> list.map { if (it.id == item.id) it.copy(category = category) else it } }
    }

    fun discard(item: ReviewItem) = viewModelScope.launch {
        prefs.updateSmsReview { list -> list.filter { it.id != item.id } }
    }

    /** Opens Add expense or Add income filled in. It stays listed until that form saves it. */
    fun edit(item: ReviewItem, description: String) {
        inbox.receive(item, description.trim().ifEmpty { item.description })
    }

    private companion object {
        const val FAILED = "Couldn't save that entry. It's back in To review."
    }
}
