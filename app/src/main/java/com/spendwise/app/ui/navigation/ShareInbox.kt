package com.spendwise.app.ui.navigation

import android.content.Intent
import com.spendwise.app.domain.SmsEntry
import com.spendwise.app.domain.parseSms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Text shared into the app, read on the device (spec §7.4). [entry] null: not a bank SMS. */
data class SharedText(val text: String, val entry: SmsEntry?)

/**
 * Hands shared text to the form that takes it: Add for a payment or anything unread, Income for money in.
 * A singleton, not a route argument, since the Add tab's existing ViewModel never sees new arguments.
 */
@Singleton
class ShareInbox @Inject constructor() {
    private val _shared = MutableStateFlow<SharedText?>(null)
    val shared: StateFlow<SharedText?> = _shared.asStateFlow()

    /** Keeps the text and returns the page to open. */
    fun receive(text: String): Any {
        val shared = SharedText(text, parseSms(text, LocalDate.now()))
        _shared.value = shared
        return if (shared.entry is SmsEntry.Earned) Income else AddExpense
    }

    /** The waiting text if [wanted] takes it; once only. */
    fun take(wanted: (SharedText) -> Boolean): SharedText? {
        val s = _shared.value ?: return null
        return if (wanted(s) && _shared.compareAndSet(s, null)) s else null
    }
}

/** ACTION_SEND with text: what Messages and most apps send from Share. */
fun Intent.sharedText(): String? =
    if (action == Intent.ACTION_SEND && type?.startsWith("text/") == true) getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } else null
