package com.spendwise.app.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.R
import com.spendwise.app.domain.NOTIF_CAUGHT_UP
import com.spendwise.app.domain.NOTIF_EMPTY
import com.spendwise.app.domain.notifBadge
import com.spendwise.app.domain.notifDetail
import com.spendwise.app.domain.notifTitle
import com.spendwise.app.domain.shortDay
import com.spendwise.app.ui.format.formatMoney
import java.time.LocalDate

/** The bell and its list (notifications.js). Opening it marks what's listed as seen; a row opens Debts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bell(onOpenDebts: () -> Unit, vm: BellViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    // Captured on open, so items stay marked "New" while the panel is up even though they're now seen
    var newOnOpen by remember { mutableStateOf(emptySet<String>()) }
    val money = { a: Double -> formatMoney(a, currency) }
    val unread = state.unread.size

    IconButton(
        onClick = { newOnOpen = state.unread; open = true; vm.markSeen() },
        modifier = Modifier.semantics { contentDescription = if (unread > 0) "Notifications, $unread new" else "Notifications" },
    ) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(notifBadge(unread)) } }) {
            Icon(painterResource(R.drawable.ic_bell), contentDescription = null)
        }
    }

    if (!open) return
    ModalBottomSheet(onDismissRequest = { open = false }, tonalElevation = 0.dp, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                if (state.items.isNotEmpty()) TextButton(onClick = { vm.clear(state.items.map { it.id }) }) { Text("Clear all") }
            }
            if (state.items.isEmpty()) {
                Text(
                    if (state.anyAtAll) NOTIF_CAUGHT_UP else NOTIF_EMPTY,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            val today = LocalDate.now()
            LazyColumn {
                items(state.items, key = { it.id }) { item ->
                    val title = notifTitle(item, money)
                    val isNew = item.id in newOnOpen
                    ListItem(
                        overlineContent = { Text(if (isNew) "New · ${shortDay(item.day, today)}" else shortDay(item.day, today)) },
                        headlineContent = { Text(title, style = if (isNew) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge) },
                        supportingContent = notifDetail(item, money).takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                        trailingContent = {
                            TextButton(onClick = { vm.clear(listOf(item.id)) }, modifier = Modifier.semantics { contentDescription = "Clear: $title" }) { Text("Clear") }
                        },
                        modifier = Modifier.clickable { open = false; onOpenDebts() },
                    )
                }
            }
        }
    }
}
