package com.spendwise.app.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.ReviewItem
import com.spendwise.app.domain.SmsEntry
import com.spendwise.app.domain.queued
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Home's "To review": bank SMS payments read on this device, saved only once approved. */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: DevicePrefs,
    private val repo: ExpenseRepository,
    private val inbox: ShareInbox,
) : ViewModel() {

    // An emptied list takes its notification with it
    val items: StateFlow<List<ReviewItem>> = prefs.smsReview
        .onEach { if (it.isEmpty()) Notifications.reviewDone(context) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun errorShown() {
        _error.value = null
    }

    /** Saves each with its description; one the server refuses goes back on the list, so nothing is lost. */
    fun approve(items: List<Pair<ReviewItem, String>>) = viewModelScope.launch {
        val writes = items.mapNotNull { (item, description) ->
            try {
                item to repo.add(item.toInput(description))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = FAILED
                null
            }
        }
        val saved = writes.map { it.first.id }.toSet()
        prefs.updateSmsReview { list -> list.filter { it.id !in saved } }
        // Offline, a write waits here until it reaches the server; the expense already shows meanwhile
        writes.forEach { (item, write) ->
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

    fun discard(item: ReviewItem) = viewModelScope.launch {
        prefs.updateSmsReview { list -> list.filter { it.id != item.id } }
    }

    /** Opens Add expense filled in. The payment stays listed: backing out of the form loses nothing. */
    fun edit(item: ReviewItem, description: String) {
        inbox.receive(SmsEntry.Spent(item.amount, item.date, item.category, item.payment, item.cardName, item.toInput(description).description))
    }

    private companion object {
        const val FAILED = "Couldn't save that payment. It's back in To review."
    }
}
