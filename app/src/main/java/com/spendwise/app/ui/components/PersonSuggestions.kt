package com.spendwise.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendwise.app.domain.KnownPerson

/**
 * Like the web's people datalist (people.js): people from past debts whose name contains what's typed.
 * Hidden once the typed name and email already are one of them.
 */
@Composable
fun PersonSuggestions(name: String, email: String, known: List<KnownPerson>, onPick: (KnownPerson) -> Unit) {
    val typed = name.trim().lowercase()
    val matches = known.filter {
        typed.isNotEmpty() && it.name.lowercase().contains(typed) && !(it.name.equals(name.trim(), true) && it.email == email.trim().lowercase())
    }.take(6)
    if (matches.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        matches.forEach { p ->
            // Two people with one name are told apart by their email (people.js)
            val twin = known.count { it.name.equals(p.name, true) } > 1
            SuggestionChip(onClick = { onPick(p) }, label = { Text(if (twin) "${p.name} · ${p.email}" else p.name) })
        }
    }
}
