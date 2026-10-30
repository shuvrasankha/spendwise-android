package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Title on the left, close on the right. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = Spacing.gutter, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = SwType.heading, color = SpendWiseTheme.colors.text, modifier = Modifier.weight(1f))
        IconAction(SwIcons.Close, "Close", onClose)
    }
}

/** A `surface` bottom sheet with a grabber and [SheetHeader]. Used for New entry, Filters and the Settings pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = SpendWiseTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface, contentColor = c.text, tonalElevation = 0.dp, shape = Radius.block,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(36.dp).height(4.dp).clip(Radius.pill).background(c.line)) },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetHeader(title, onDismiss)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

enum class EntryType(val label: String) { Expense("Expense"), Income("Income"), Debt("Debt"), Invest("Invest") }

/** The big centred amount. [value] is the raw text the ViewModel keeps; the symbol sits in front. */
@Composable
fun AmountField(value: String, onValueChange: (String) -> Unit, symbol: String, modifier: Modifier = Modifier) {
    val c = SpendWiseTheme.colors
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Text(symbol, style = SwType.title, color = c.textMuted, modifier = Modifier.padding(bottom = 6.dp, end = 4.dp))
        BasicTextField(
            value, onValueChange,
            textStyle = SwType.display.copy(color = c.text, textAlign = TextAlign.Start),
            cursorBrush = SolidColor(c.text), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text("0", style = SwType.display, color = c.textFaint); inner() } },
            modifier = Modifier.width(IntrinsicWidth),
        )
    }
}

// Wide enough for a lakh-crore figure; the field never clips what's typed
private val IntrinsicWidth = 220.dp

/** Today / Yesterday / Pick a date. [date] is the form's `YYYY-MM-DD`, in the local calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateChips(date: String, today: LocalDate, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    val yesterday = today.minusDays(1).toString()
    val custom = date != today.toString() && date != yesterday
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SwChip("Today", date == today.toString(), { onChange(today.toString()) })
        SwChip("Yesterday", date == yesterday, { onChange(yesterday) })
        SwChip(if (custom) date else "Pick a date", custom, { picking = true }, leading = SwIcons.Calendar)
    }
    if (picking) {
        // The picker speaks UTC midnights; reading them back as UTC dates keeps the calendar day the user tapped
        val state = rememberDatePickerState(initialSelectedDateMillis = runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/**
 * New entry: type → amount → [fields] → date → notes → primary button. The caller owns every value, so the existing
 * ViewModel save functions stay the only writers.
 */
@Composable
fun NewEntrySheet(
    type: EntryType,
    onTypeChange: (EntryType) -> Unit,
    onDismiss: () -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    symbol: String,
    date: String,
    today: LocalDate,
    onDateChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    primaryLabel: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
    fields: @Composable ColumnScope.() -> Unit,
) = SwSheet("New entry", onDismiss) {
    Column(modifier.fillMaxWidth().padding(horizontal = 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.padding(horizontal = Spacing.gutter)) {
            SegmentedControl(EntryType.entries.map { it.label }, type.ordinal, { onTypeChange(EntryType.entries[it]) })
        }
        AmountField(amount, onAmountChange, symbol)
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp), content = fields)
        DateChips(date, today, onDateChange)
        Box(Modifier.padding(horizontal = Spacing.gutter)) { SwField(notes, onNotesChange, "Notes", singleLine = false) }
        Box(Modifier.padding(horizontal = Spacing.gutter)) { SwButton(primaryLabel, onSave, Modifier.fillMaxWidth(), enabled = saveEnabled) }
    }
}

@DarkLight
@Composable
private fun SheetPartsPreview() = PreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SheetHeader("New entry", {})
        SegmentedControl(EntryType.entries.map { it.label }, 0, {}, Modifier.padding(horizontal = Spacing.gutter))
        AmountField("1250", {}, "₹")
        DateChips("2026-10-30", LocalDate.of(2026, 10, 30), {})
    }
}
