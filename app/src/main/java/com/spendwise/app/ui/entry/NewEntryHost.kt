package com.spendwise.app.ui.entry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.Currency
import com.spendwise.app.domain.I_OWE
import com.spendwise.app.domain.INCOME_PAYMENT_TYPES
import com.spendwise.app.domain.INVEST_PAYMENTS
import com.spendwise.app.domain.MAX_SPLIT_PEOPLE
import com.spendwise.app.domain.ONLINE
import com.spendwise.app.domain.PAYMENT_METHODS
import com.spendwise.app.domain.THEY_OWE
import com.spendwise.app.domain.isCard
import com.spendwise.app.domain.splitSummary
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.EntryType
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.NewEntrySheet
import com.spendwise.app.ui.components.PersonSuggestions
import com.spendwise.app.ui.components.SegmentedControl
import com.spendwise.app.ui.components.SettingsGroup
import com.spendwise.app.ui.components.SettingsRow
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.Trailing
import com.spendwise.app.ui.debts.DebtsPage
import com.spendwise.app.ui.debts.DebtsViewModel
import com.spendwise.app.ui.debts.readContact
import com.spendwise.app.ui.expenses.ExpenseEditorViewModel
import com.spendwise.app.ui.format.formatAmount
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.income.IncomeFormActions
import com.spendwise.app.ui.income.IncomePage
import com.spendwise.app.ui.income.IncomeViewModel
import com.spendwise.app.ui.invest.InvestPage
import com.spendwise.app.ui.invest.InvestViewModel
import com.spendwise.app.ui.invest.plain
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What one entry type puts in the sheet's shared frame; the ViewModels stay the only writers. */
private class Frame(
    val amount: String, val onAmount: (String) -> Unit,
    val date: String, val onDate: (String) -> Unit,
    val notes: String, val onNotes: (String) -> Unit,
    val label: String, val onSave: () -> Unit,
    val error: String? = null, val amountHint: String = "0",
    val fields: @Composable ColumnScope.() -> Unit,
)

/**
 * The New entry sheet, opened from the + button and from each page's primary button.
 * [onSaved] gets the confirmation text when an Income, Debt or Invest entry was saved; an expense reports through its Undo.
 */
@Composable
fun NewEntryHost(
    type: EntryType,
    onTypeChange: (EntryType) -> Unit,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onVoice: () -> Unit,
    onNewHolding: () -> Unit,
    expense: ExpenseEditorViewModel,
    income: IncomeViewModel,
    debts: DebtsViewModel,
    invest: InvestViewModel,
) {
    val today = LocalDate.now()
    val currency by expense.currency.collectAsStateWithLifecycle()

    val e by expense.state.collectAsStateWithLifecycle()
    val inc by income.form.collectAsStateWithLifecycle()
    val incMsg by income.message.collectAsStateWithLifecycle()
    val incPage by income.page.collectAsStateWithLifecycle()
    val debt by debts.form.collectAsStateWithLifecycle()
    val debtMsg by debts.formMessage.collectAsStateWithLifecycle()
    val debtPage by debts.page.collectAsStateWithLifecycle()
    val inv by invest.form.collectAsStateWithLifecycle()
    val invMsg by invest.formMessage.collectAsStateWithLifecycle()
    val invPage by invest.page.collectAsStateWithLifecycle()
    val chosenId by invest.chosenId.collectAsStateWithLifecycle()
    val invToast by invest.toast.collectAsStateWithLifecycle()

    // A save sets a success message (or, for Invest, a toast) after the click; an old one from before must not close the sheet
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(incMsg) { incMsg?.let { if (saving) { saving = false; if (it.success) onSaved(it.text) } } }
    LaunchedEffect(debtMsg) { debtMsg?.let { if (saving) { saving = false; if (it.success) onSaved(it.text) } } }
    LaunchedEffect(invMsg) { if (saving && invMsg != null) saving = false }
    LaunchedEffect(invToast) { invToast?.let { if (saving) { saving = false; invest.toastShown(); onSaved(it.text) } } }

    val frame = when (type) {
        EntryType.Expense -> Frame(
            e.form.amount, expense::setAmount, e.form.date, expense::setDate, e.form.notes, expense::setNotes,
            "Add Expense", expense::save, e.message?.takeIf { !it.success }?.text,
        ) { ExpenseFields(e, expense, currency, onVoice) }
        EntryType.Income -> {
            val ready = incPage as? IncomePage.Ready
            Frame(
                inc.amount, income::setAmount, inc.date, income::setDate, inc.notes, income::setNotes,
                "Add income", { saving = true; income.add() }, incMsg?.takeIf { !it.success }?.text,
            ) { IncomeFields(inc.source, inc.paymentType, inc.bank, income, ready?.sources.orEmpty(), ready?.banks.orEmpty()) }
        }
        EntryType.Debt -> {
            val ready = debtPage as? DebtsPage.Ready
            Frame(
                debt.amount, debts::setAmount, debt.date, debts::setDate, debt.notes, debts::setNotes,
                "Add debt", { saving = true; debts.add() }, debtMsg?.takeIf { !it.success }?.text,
            ) { DebtFields(debt.type, debt.person, debt.email, debts, ready?.known.orEmpty()) }
        }
        EntryType.Invest -> {
            val ready = invPage as? InvestPage.Ready
            val chosen = ready?.let { invest.chosen(it, chosenId) }
            val sip = ready?.portfolio?.rows?.firstOrNull { it.holding.id == chosen?.id }?.sip
            Frame(
                inv.amount, invest::setAmount, inv.date, invest::setDate, inv.notes, invest::setNotes,
                "Put money in", { saving = true; invest.add() }, invMsg?.text,
                // An empty amount uses the SIP's, so it shows as the hint
                amountHint = sip?.let { plain(it.amount) } ?: "0",
            ) { InvestFields(ready, chosen?.id, inv.payment, inv.repeat ?: (sip != null), invest, onNewHolding) }
        }
    }

    NewEntrySheet(
        type, onTypeChange, onDismiss, frame.amount, frame.onAmount, currency.symbol, frame.date, today, frame.onDate,
        frame.notes, frame.onNotes, frame.label, frame.onSave, amountHint = frame.amountHint, error = frame.error, fields = frame.fields,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

@Composable
private fun ChipScroll(content: @Composable () -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }

// The frame pads its fields by the gutter, so labels and groups here take no inset of their own
@Composable
private fun Label(text: String) = FieldLabel(text, inset = 0.dp)

@Composable
private fun ExpenseFields(state: com.spendwise.app.ui.expenses.EditorState, vm: ExpenseEditorViewModel, currency: Currency, onVoice: () -> Unit) {
    val form = state.form
    var more by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwButton("Speak or scan", onVoice, style = ButtonStyle.Secondary, compact = true, leading = SwIcons.Mic)
        Label("Category")
        ChipFlow {
            state.tiles.forEach { (category, label) -> SwChip(label, form.category == category, { vm.setCategory(category) }, leading = SwIcons.forCategory(category)) }
        }
        SwField(
            form.description, vm::setDescription, "Description",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )
        Label("Paid with")
        ChipFlow { PAYMENT_METHODS.forEach { SwChip(it, form.payment == it, { vm.setPayment(it) }) } }
        if (isCard(form.payment)) SwField(form.cardName, vm::setCardName, "Card name (optional)")

        SwButton(if (more) "Fewer options" else "More options", { more = !more }, style = ButtonStyle.Text, compact = true)
        if (more) {
            SettingsGroup(null, inset = 0.dp) {
                SettingsRow("Repeats every month", trailing = Trailing.Switch(form.repeat, vm::setRepeat))
            }
            if (form.repeat) {
                Text(
                    "Rent, subscriptions, SIPs, EMIs: the Dashboard offers it again when it's due. For a loan or a no-cost EMI, enter how many payments it has.",
                    style = SwType.label, color = SpendWiseTheme.colors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        SwField(form.emiTotal, vm::setEmiTotal, "Total payments", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Box(Modifier.weight(1f)) {
                        SwField(form.emiBefore, vm::setEmiBefore, "Already paid", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
            SwField(
                form.tagDraft, vm::setTagDraft, "Tags (optional): comma or space",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.commitTagDraft() }),
            )
            if (form.tags.isNotEmpty()) ChipFlow { form.tags.forEach { SwChip("#$it", true, { vm.removeTag(it) }, leading = SwIcons.Close) } }
            SplitFields(state, currency, vm)
        }
    }
}

/** "Split with others": your share is the expense; each person owes you theirs (split.js). */
@Composable
private fun SplitFields(state: com.spendwise.app.ui.expenses.EditorState, currency: Currency, vm: ExpenseEditorViewModel) {
    val form = state.form
    val (hint, note) = splitSummary(form.split, form.amount) { formatAmount(it, currency) }
    Label("Split with others · $hint")
    form.split.forEachIndexed { i, row ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwField(
                    row.name, { vm.setSplitPerson(i, row.copy(name = it.take(100))) }, "Name",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                PersonSuggestions(row.name, row.email, state.known) { vm.pickSplitPerson(i, it) }
                if (state.canShare) {
                    SwField(
                        row.email, { vm.setSplitPerson(i, row.copy(email = it.take(254))) }, "Email (optional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
            }
            com.spendwise.app.ui.components.IconAction(SwIcons.Close, "Remove person ${i + 1}", { vm.removeSplitPerson(i) })
        }
    }
    if (form.split.size < MAX_SPLIT_PEOPLE) SwButton("Add person", vm::addSplitPerson, style = ButtonStyle.Secondary, compact = true)
    Text(note, style = SwType.label, color = SpendWiseTheme.colors.textMuted)
}

@Composable
private fun IncomeFields(source: String, paymentType: String, bank: String, actions: IncomeFormActions, sources: List<String>, banks: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwField(source, actions::setSource, "From")
        if (sources.isNotEmpty()) ChipScroll { sources.forEach { SwChip(it, false, { actions.pickSource(it) }) } }
        Label("Received as")
        SegmentedControl(INCOME_PAYMENT_TYPES, INCOME_PAYMENT_TYPES.indexOf(paymentType).coerceAtLeast(0), { actions.setPaymentType(INCOME_PAYMENT_TYPES[it]) })
        if (paymentType == ONLINE) {
            SwField(bank, actions::setBank, "Bank or wallet")
            // Like the web's datalist: past banks containing what's typed, most used first
            val typed = bank.trim().lowercase()
            val matches = banks.filter { it.lowercase().contains(typed) && !it.equals(bank.trim(), ignoreCase = true) }.take(6)
            if (matches.isNotEmpty()) ChipScroll { matches.forEach { SwChip(it, false, { actions.setBank(it) }) } }
        }
    }
}

@Composable
private fun DebtFields(type: String, person: String, email: String, vm: DebtsViewModel, known: List<com.spendwise.app.domain.KnownPerson>) {
    val types = listOf(THEY_OWE to "They owe me", I_OWE to "I owe them")
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    // The system picker hands over just the chosen contact, so no contacts permission is asked for
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { readContact(resolver, uri) }?.let(vm::pickPerson) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedControl(types.map { it.second }, types.indexOfFirst { it.first == type }.coerceAtLeast(0), { vm.setType(types[it].first) })
        SwField(person, vm::setPerson, "Person", leading = SwIcons.People)
        PersonSuggestions(person, email, known, vm::pickPerson)
        SwButton("Choose from contacts", { pickContact.launch(null) }, style = ButtonStyle.Text, compact = true)
        if (vm.canShare) {
            SwField(
                email, vm::setEmail, "Their email (optional)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                supportingText = "If they use SpendWise with this email, it shows in their Debts too.",
            )
        }
    }
}

@Composable
private fun InvestFields(page: InvestPage.Ready?, chosenId: String?, payment: String, repeat: Boolean, vm: InvestViewModel, onNewHolding: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (page == null || page.holdings.isEmpty()) {
            Text("Make a holding first, such as a mutual fund or a deposit, then put money into it.", style = SwType.body, color = SpendWiseTheme.colors.textMuted)
            SwButton("New holding", { vm.newHolding(); onNewHolding() }, style = ButtonStyle.Secondary, compact = true)
            return@Column
        }
        Label("Into")
        ChipScroll {
            page.holdings.forEach { h -> SwChip(h.name, h.id == chosenId, { vm.choose(h.id) }) }
            SwChip("New holding", false, { vm.newHolding(); onNewHolding() }, leading = SwIcons.Plus)
        }
        Label("Paid with")
        ChipFlow { INVEST_PAYMENTS.forEach { (id, label) -> SwChip(label, payment == id, { vm.setPayment(id) }) } }
        SettingsGroup(null, inset = 0.dp) {
            SettingsRow("Every month (SIP)", trailing = Trailing.Switch(repeat, vm::setRepeat))
        }
    }
}
