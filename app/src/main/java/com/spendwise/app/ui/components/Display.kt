package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** The screen's hero number: label, symbol + display amount, sub line. [positive] is for money in. */
@Composable
fun Metric(label: String, symbol: String, amount: String, modifier: Modifier = Modifier, sub: String? = null, positive: Boolean = false) {
    val c = SpendWiseTheme.colors
    val color = if (positive) c.positive else c.text
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = SwType.label, color = c.textMuted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(symbol, style = SwType.title, color = color, modifier = Modifier.padding(bottom = 6.dp, end = 2.dp))
            Text(amount, style = SwType.display, color = color)
        }
        if (sub != null) Text(sub, style = SwType.label, color = c.textMuted)
    }
}

data class Stat(val label: String, val value: String, val icon: ImageVector? = null, val valueColor: Color = Color.Unspecified, val sub: String? = null, val onClick: (() -> Unit)? = null)

/** 2×2 grid inside one bordered block, split by 1dp lines. [stats] is up to four items, row by row. */
@Composable
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier) {
    val c = SpendWiseTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).clip(Radius.block).border(1.dp, c.line, Radius.block)) {
        stats.chunked(2).forEachIndexed { r, pair ->
            if (r > 0) HorizontalDivider(color = c.line)
            Row(Modifier.height(IntrinsicSize.Min)) {
                pair.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                    Column(
                        Modifier.weight(1f).then(if (s.onClick != null) Modifier.clickable(role = Role.Button, onClick = s.onClick) else Modifier).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (s.icon != null) Icon(s.icon, null, Modifier.size(16.dp), tint = c.textMuted)
                            Text(s.label, style = SwType.label, color = c.textMuted)
                        }
                        Text(s.value, style = SwType.stat, color = s.valueColor.takeOrElse { c.text })
                        if (s.sub != null) Text(s.sub, style = SwType.caption, color = c.textMuted)
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}


/** Title plus at most one text action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = SpendWiseTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = SwType.heading, color = c.text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, style = SwType.labelStrong, color = c.textMuted, modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(vertical = 8.dp))
        }
    }
}

/** A flat list row: 36dp icon tile, title + meta, amount + sub. [divider] draws the 1dp line above the row (false for the first). */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    amount: String? = null,
    sub: String? = null,
    icon: ImageVector? = null,
    amountColor: Color = Color.Unspecified,
    metaColor: Color = Color.Unspecified,
    metaLines: Int = 1,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = SpendWiseTheme.colors
    Column(modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(Modifier.padding(horizontal = Spacing.gutter), color = c.line)
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.gutter, vertical = Spacing.rowV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Box(Modifier.size(36.dp).clip(Radius.tile).background(c.surface), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = c.textMuted)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = SwType.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta != null) Text(meta, style = SwType.caption, color = metaColor.takeOrElse { c.textMuted }, maxLines = metaLines, overflow = TextOverflow.Ellipsis)
            }
            if (amount != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(amount, style = SwType.bodyStrong, color = amountColor.takeOrElse { c.text })
                    if (sub != null) Text(sub, style = SwType.caption, color = c.textMuted)
                }
            }
        }
    }
}

/** Overline date on the left, that day's total on the right. Sticky in History. */
@Composable
fun DayHeader(date: String, total: String, modifier: Modifier = Modifier) {
    val c = SpendWiseTheme.colors
    Row(
        modifier.fillMaxWidth().background(c.bg).semantics { heading() }.padding(horizontal = Spacing.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date.uppercase(), style = SwType.overline, color = c.textMuted, modifier = Modifier.weight(1f))
        Text(total, style = SwType.label, color = c.textMuted)
    }
}

/** A legend line for a stacked bar: a colour swatch, a title and a note under it. */
@Composable
fun LegendRow(color: Color, title: String, sub: String?, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = SpendWiseTheme.colors
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).clip(Radius.pill).background(color))
        Column(Modifier.weight(1f)) {
            Text(title, style = SwType.body, color = c.text)
            if (sub != null) Text(sub, style = SwType.caption, color = c.textMuted)
        }
        if (trailing != null) Text(trailing, style = SwType.bodyStrong, color = c.text)
    }
}

/** Overline label above a group of fields or chips. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, inset: Dp = Spacing.gutter) =
    Text(text.uppercase(), style = SwType.overline, color = SpendWiseTheme.colors.textMuted, modifier = modifier.padding(horizontal = inset))

sealed interface Trailing {
    data object Chevron : Trailing
    data object None : Trailing
    data class Value(val text: String) : Trailing
    data class Switch(val checked: Boolean, val onChange: (Boolean) -> Unit) : Trailing
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: Trailing = Trailing.Chevron,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = SpendWiseTheme.colors
    Column(modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(Modifier.padding(start = if (icon != null) 52.dp else 16.dp), color = c.line)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = c.textMuted)
            Text(title, style = SwType.body, color = c.text, modifier = Modifier.weight(1f))
            when (trailing) {
                Trailing.Chevron -> Icon(SwIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.textFaint)
                Trailing.None -> Unit
                is Trailing.Value -> {
                    Text(trailing.text, style = SwType.label, color = c.textMuted)
                    Icon(SwIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.textFaint)
                }
                is Trailing.Switch -> SwSwitch(trailing.checked, trailing.onChange)
            }
        }
    }
}

/** Overline label above one `surface` block; rows inside pass divider = true except the first. */
@Composable
fun SettingsGroup(label: String?, modifier: Modifier = Modifier, inset: Dp = Spacing.gutter, content: @Composable ColumnScope.() -> Unit) {
    val c = SpendWiseTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = inset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) Text(label.uppercase(), style = SwType.overline, color = c.textMuted, modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.fillMaxWidth().clip(Radius.block).background(c.surface), content = content)
    }
}

/** Progress is accent; [over] (past the limit by default) turns it negative. [pace] (0..1) marks how far through the period we are. */
@Composable
fun BudgetBar(progress: Float, modifier: Modifier = Modifier, pace: Float? = null, over: Boolean = progress > 1f) {
    val c = SpendWiseTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill).background(c.line))
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(Radius.pill).background(if (over) c.negative else c.accent))
        if (pace != null) Box(Modifier.offset(x = maxWidth * pace.coerceIn(0f, 1f) - 1.dp).width(2.dp).height(12.dp).background(c.text))
    }
}

/** A small outlined status label, e.g. "On track". */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val c = SpendWiseTheme.colors
    Text(
        text, style = SwType.labelStrong, color = color.takeOrElse { c.text },
        modifier = modifier.border(1.dp, c.line, Radius.pill).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

data class BarSegment(val weight: Float, val color: Color)

@Composable
fun StackedBar(segments: List<BarSegment>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(8.dp).clip(Radius.pill).background(SpendWiseTheme.colors.line), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.filter { it.weight > 0f }.forEach { Box(Modifier.weight(it.weight).fillMaxHeight().background(it.color)) }
    }
}

/** One bar per label; the current one is accent, the rest `line`. */
@Composable
fun WeekBars(values: List<Float>, labels: List<String>, currentIndex: Int, modifier: Modifier = Modifier) {
    val c = SpendWiseTheme.colors
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Row(modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight((v / max).coerceIn(0.03f, 1f)).clip(Radius.segment).background(if (i == currentIndex) c.accent else c.line))
                }
                Spacer(Modifier.height(6.dp))
                Text(labels.getOrElse(i) { "" }, style = SwType.micro, color = if (i == currentIndex) c.text else c.textFaint, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@DarkLight
@Composable
private fun DisplayPreview() = PreviewSurface {
    val c = SpendWiseTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.section), modifier = Modifier.padding(vertical = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Spent in October", "₹", "6,68,162", sub = "of ₹8,00,000 budget")
            Box(Modifier.padding(horizontal = Spacing.gutter)) { BudgetBar(0.62f, pace = 0.7f) }
        }
        StatGrid(
            listOf(
                Stat("Income", "₹1,20,000", SwIcons.MoneyIn, c.positive), Stat("Saved", "₹32,000", SwIcons.Wallet),
                Stat("You owe", "₹4,500", SwIcons.MoneyOut, c.negative), Stat("Owed to you", "₹9,000", SwIcons.People, c.positive),
            ),
        )
        Column {
            SectionHeader("Recent", action = "See all")
            DayHeader("Today", "₹1,240")
            ListRow("Lunch", meta = "Food · UPI", amount = "₹420", icon = SwIcons.Wallet)
            ListRow("Salary", meta = "Income", amount = "+₹1,20,000", amountColor = c.positive, icon = SwIcons.MoneyIn, divider = true)
            ListRow("Rent", meta = "Over by ₹2,000", metaColor = c.negative, amount = "₹22,000", sub = "of ₹20,000", divider = true)
        }
        SettingsGroup("App") {
            SettingsRow("Appearance", icon = SwIcons.Settings, trailing = Trailing.Value("System"))
            SettingsRow("Reminder", icon = SwIcons.Bell, trailing = Trailing.Switch(true) {}, divider = true)
        }
        StackedBar(listOf(BarSegment(5f, c.text), BarSegment(3f, c.textMuted), BarSegment(2f, c.textFaint)), Modifier.padding(horizontal = Spacing.gutter))
        Box(Modifier.padding(horizontal = Spacing.gutter)) {
            WeekBars(listOf(3f, 5f, 2f, 8f, 4f, 6f, 1f), listOf("M", "T", "W", "T", "F", "S", "S"), currentIndex = 3)
        }
    }
}
