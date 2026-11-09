package com.spendwise.app.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.expenses.ExpenseRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.Card
import com.spendwise.app.domain.CardSpend
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.cardKey
import com.spendwise.app.domain.cardColours
import com.spendwise.app.domain.cardSpending
import com.spendwise.app.domain.currencyOf
import com.spendwise.app.domain.sanitize
import com.spendwise.app.domain.savedCards
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject

sealed interface CardsPage {
    data object Loading : CardsPage
    data class Failed(val message: String) : CardsPage
    /** [colours]: each card's colour name, by card key. */
    data class Ready(val spends: List<CardSpend>, val expenses: List<Expense>, val colours: Map<String, String>) : CardsPage
}

/** More › Cards: spending per saved card; add, rename or merge, and take a card off the list. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repo: ExpenseRepository,
    private val prefs: DevicePrefs,
) : ViewModel() {
    private val reload = MutableStateFlow(0)

    val page: StateFlow<CardsPage> = reload.flatMapLatest {
        combine(repo.expenses(), prefs.addedCards, prefs.hiddenCards, prefs.cardColours) { all, added, hidden, chosen ->
            val cards = savedCards(all, added, hidden)
            CardsPage.Ready(cardSpending(all, cards, LocalDate.now()), all, cardColours(cards, chosen)) as CardsPage
        }.catch { emit(CardsPage.Failed("Couldn't load your cards. Check your connection and try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardsPage.Loading)

    val currency: StateFlow<Currency> = prefs.currency.stateIn(viewModelScope, SharingStarted.Eagerly, currencyOf(null))

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun noticeShown() {
        _notice.value = null
    }

    fun retry() = reload.value++

    /** A null [colour] leaves the card to the automatic pick. */
    fun add(name: String, type: String, colour: String? = null) {
        val clean = sanitize(name.trim().replace(Regex("\\s+"), " "), 100)
        if (clean.isEmpty()) return
        val card = Card(clean, type)
        viewModelScope.launch {
            colour?.let { prefs.setCardColour(card.key, it) }
            prefs.addCard(card)
        }
    }

    /**
     * A new name on each of the card's expenses; a name another card has merges the two. A card not used yet is
     * renamed on this device only.
     */
    fun rename(card: Card, name: String) {
        val ready = page.value as? CardsPage.Ready ?: return
        val clean = sanitize(name.trim().replace(Regex("\\s+"), " "), 100)
        if (clean.isEmpty() || clean == card.name) return
        // Merging into a card already listed takes its spelling
        val target = ready.spends.map { it.card }.firstOrNull { it.key == cardKey(clean, card.type) && it.key != card.key }?.name ?: clean
        val ids = ready.expenses.filter { cardKey(it.cardName, it.payment) == card.key }.map { it.id }
        viewModelScope.launch {
            // The new name keeps the colour, unless it merges into a card that has its own
            if (target == clean) ready.colours[card.key]?.let { prefs.setCardColour(cardKey(target, card.type), it) }
            prefs.hideCard(card)
            prefs.addCard(Card(target, card.type))
            if (ids.isEmpty()) return@launch
            try {
                repo.renameCard(ids, target).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = "Couldn't rename every expense. Try again when you're online."
            }
        }
    }

    fun setColour(card: Card, colour: String) = viewModelScope.launch { prefs.setCardColour(card.key, colour) }

    /** Off the list on this device; its expenses keep their card name. */
    fun remove(card: Card) = viewModelScope.launch { prefs.hideCard(card) }
}
