package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** A 48dp tap target with a 22dp icon, for TopBar actions. */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = SpendWiseTheme.colors.text) }
}

/** Home: greeting over the name, actions on the right. Insets are the caller's: [statusBarInset] adds the status bar. */
@Composable
fun HomeTopBar(greeting: String, name: String, modifier: Modifier = Modifier, statusBarInset: Boolean = false, actions: @Composable () -> Unit = {}) {
    val c = SpendWiseTheme.colors
    Row(
        modifier.then(if (statusBarInset) Modifier.statusBarsPadding() else Modifier).fillMaxWidth()
            .padding(start = Spacing.gutter, end = 8.dp).height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = SwType.label, color = c.textMuted)
            Text(name, style = SwType.heading, color = c.text)
        }
        actions()
    }
}

/** A tab's title (28sp) with actions. */
@Composable
fun TabTopBar(title: String, modifier: Modifier = Modifier, statusBarInset: Boolean = false, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.then(if (statusBarInset) Modifier.statusBarsPadding() else Modifier).fillMaxWidth()
            .padding(start = Spacing.gutter, end = 8.dp).height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = SwType.title, color = SpendWiseTheme.colors.text, modifier = Modifier.weight(1f))
        actions()
    }
}

/** A page opened from More: back button and title. */
@Composable
fun SubPageTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, statusBarInset: Boolean = false, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.then(if (statusBarInset) Modifier.statusBarsPadding() else Modifier).fillMaxWidth().padding(horizontal = 8.dp).height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(SwIcons.Back, "Back", onBack)
        Text(title, style = SwType.heading, color = SpendWiseTheme.colors.text, modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions()
    }
}

enum class BottomTab(val label: String, val icon: ImageVector) {
    Home("Home", SwIcons.Home), History("History", SwIcons.History), Insights("Insights", SwIcons.Insights), More("More", SwIcons.More),
}

/** Home · History · [+] · Insights · More. [selected] null when the page is under More (Income, Debts, ...). */
@Composable
fun SpendWiseBottomBar(selected: BottomTab?, onSelect: (BottomTab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = SpendWiseTheme.colors
    Column(modifier.fillMaxWidth().background(c.bg)) {
        HorizontalDivider(color = c.line)
        Row(Modifier.navigationBarsPadding().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun item(tab: BottomTab) {
                val on = tab == selected
                val tint = if (on) c.text else c.textFaint
                Column(
                    Modifier.weight(1f).height(64.dp).clickable(role = Role.Tab) { onSelect(tab) }.semantics { this.selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, null, Modifier.size(22.dp), tint = tint)
                    Spacer(Modifier.height(4.dp))
                    Text(tab.label, style = SwType.micro, color = tint)
                }
            }
            item(BottomTab.Home)
            item(BottomTab.History)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(c.accent).clickable(role = Role.Button, onClick = onAdd)
                        .semantics { contentDescription = "New entry" },
                    contentAlignment = Alignment.Center,
                ) { Icon(SwIcons.Plus, null, Modifier.size(24.dp), tint = c.onAccent) }
            }
            item(BottomTab.Insights)
            item(BottomTab.More)
        }
    }
}

@DarkLight
@Composable
private fun BarsPreview() = PreviewSurface {
    Column {
        HomeTopBar("Good evening", "Shuvra") { IconAction(SwIcons.Bell, "Notifications", {}) }
        TabTopBar("History") { IconAction(SwIcons.Download, "Export", {}) }
        SubPageTopBar("Income", {})
        SpendWiseBottomBar(BottomTab.Home, {}, {})
    }
}
