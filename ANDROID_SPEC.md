# SpendWise Android — Specification

Source: the web app at `../spendwise` (read only), at commit `26129c2` (Merge PR #55). Every item cites
`file › function`. Paths are relative to `../spendwise/`. When the web app changes, re-check the
cited functions.

---

## 0. Read this first: findings that change the plan

1. **No field in Firestore is encrypted today.** The web app writes plain values with
   `encoding: "plain"`. An XOR scheme was used for 10 days in March 2026 and then removed, so the
   only crypto concern is legacy data. See §5. **Decided:** Android writes plain values too, and
   has no crypto class (§8, Q1).
2. **The web app is hosted on GitHub Pages, not Netlify.** Netlify was removed in commit `dbe62c7`.
   It also uses a **Cloudflare Worker** (`worker/`) for AI and push notifications. See §7.
   **Decided:** Android v1 never calls the Worker (§8, Q4).
3. **The web app does much more than the original package layout covered.** It also has
   investments, debts shared by email, splitting a bill, budgets, savings goals, repeating bills
   and EMIs, a forecast, AI features, voice/receipt/SMS entry, CSV import, backup and restore, push
   reminders and an app lock. **Decided:** v1 scope is in §8.1. Web quirks are ported exactly; see §9.
4. **Currency and theme are per device** (browser `localStorage`), not in Firestore. Amounts are
   stored as plain numbers with no currency attached. See §3.8.
5. **`amount` is not always a number in old documents**, and `settled` is not always a boolean.
   Readers must be tolerant. See §3.7.
6. **No Firestore composite indexes are needed.** Every query is a single `where` on one field, and
   sorting happens on the client.

---

## 1. Screens

All pages except the sign-in screen require a signed-in user with a verified email (§2.1). The web
app is a set of separate HTML pages. Each one loads all of the user's records with
`where('uid','==',uid)`, then filters and sorts on the client.

### 1.0 Navigation (applies to every screen)

- **Phone tab bar**, 5 icon-only tabs: **Dashboard** (`index.html`), **History**, **Add**
  (`expense.html`), **Insights**, **More**. More opens a sheet with **Income**, **Investments**,
  **Debts** and **Settings**. Sources: `index.html` bottom nav; `assets/js/modules/nav.js` (the
  More sheet's pages list); `assets/js/event-delegation.js` (`navigateTo*` actions).
  - Holding **Add** opens voice entry (`nav.js`: hold-+ → `expense.html?voice=1`).
- **Desktop**: a sidebar with the same destinations. Android is phone-first, so the tab bar is the
  model.
- **Header on every signed-in page**:
  - Avatar menu (`modules/page-shell.js › updateUserAvatar, installMenus`): profile and Log out.
  - Notification bell (`modules/notifications.js`), §7.6.
  - Offline/pending-sync badge (`modules/offline-queue.js › updatePendingBadge`,
    `modules/network-status.js`).
- **Signed-out user**: on `index.html` the sign-in screen is shown. Every other page redirects to
  `index.html` (`page-shell.js › requireUser`; `dashboard.js › onAuthStateChanged`).
- **Toasts**: `page-shell.js › showToast`. Error toasts stay until tapped (`accessibility.js`).
- **Deep links** (cold-start entry points):
  - `index.html#history|add|insights|dashboard` (`dashboard.js › onAuthStateChanged`).
  - `history.html#income`.
  - `history.html?tab=&q=&from=&to=&cat=` (`dashboard.js › initHistory`).
  - `expense.html?voice=1`, `?source=shortcut`, and the share target `?title=&text=`
    (`manifest.json`).

### 1.1 Sign-in screen (inside `index.html`, `#auth-screen`)

- **Purpose**: sign in or create an account.
- **UI**:
  - Two tabs, Login and Sign up (`dashboard.js › switchTab`).
  - Login fields: Email, Password; a "Forgot password?" link; "Continue with Google".
  - Sign-up fields: Full Name, Email, Password; "Continue with Google".
  - Forgot-password dialog with two steps: enter email, then "We sent a password reset link to …".
- **States**: error/success text in `#auth-error`, shown in green on success
  (`dashboard.js › showAuthError`). Error messages come from `utils/helpers.js › friendlyErr`.
  The page loader shows until the auth state is known (`page-shell.js › dismissPageLoader`).
- **Navigation**: on success the app content shows in place, on the Dashboard.

### 1.2 Dashboard (`index.html`, `#page-dashboard`) — `assets/js/dashboard.js`

- **Purpose**: this month at a glance.
- **UI, top to bottom** (`dashboard.js › renderHome`, unless another function is named):
  1. **Hero, "{Month} so far"**:
     - Spent this month (whole currency units).
     - A meter against the budget, or against income if there's no budget.
     - Pace line: "₹X a day · on track for ₹Y by the end of {Month}".
     - Link: "Set a monthly budget" / "Change budget".
  2. **Today**:
     - Today's spending total with an entry count.
     - A meter comparing today with the month's daily average (with an average tick).
     - Up to 5 rows: category icon, title, "category · payment · waiting to sync", amount.
  3. **Tiles**:
     - Income this month.
     - Saved (income − spent, as % of income, "· ₹X invested").
     - You owe.
     - Owed to you.
  4. **Due this month** (`renderDue`), hidden when empty:
     - Repeating bills and EMIs whose day has come.
     - Each row has **Add**, **Skip month** and **Stop repeating**.
  5. **Money score** (0–100) with a label, a tip and an expandable "What makes up your score" with
     per-part bars.
  6. **Savings goals** (`renderGoals`):
     - Ring progress, "₹X of ₹Y", "₹Z a month · by Mar 2027".
     - Buttons: **Add money**, **Edit**, **New goal**.
     - Header: "₹N a month · covered / ₹M left this month".
  7. **Where it went**: top categories this month as labelled bars, with budget limits where set.
  8. **Spending over time**:
     - Month and year period picker (`initPeriodPicker`, `updatePeriodSummary`).
     - Summary bar: total, count, average per day, largest.
     - **Daily / Weekly / Monthly / Yearly** tabs (`switchTableTab`).
     - Line chart (`renderTrendChart`, Chart.js).
     - Expense table: phones get "Show more", 10 at a time; desktop gets pages of 10
       (`renderDashboardTable`, `showTableRows`).
- **States**:
  - Loading: on the first visit, a card shimmer (`showLoader`) and table skeleton
    (`showTableSkeleton`). Later visits render at once from a sessionStorage copy, then sync
    quietly (`loadFromCache`, then `loadExpenses(true)`).
  - Empty:
    - New account: "Add your first expense to start tracking. It takes a few seconds."
    - "Nothing added today yet. Add an expense".
    - "No expenses this month yet." / "Your top categories show here once you add expenses."
    - Goals: "Saving for a trip, a phone or a rainy day? Add a goal…".
    - Score: "No score yet · Add this month's income to see your score".
    - Meter: "Add this month's income to see how much is left."
  - Error: toast "Error loading data." (`loadExpenses`). Income, debt and holding load errors are
    logged only.
- **Navigation out**:
  - Add expense, Income, Investments (from a goal's "₹X invested"), Debts (from the tiles).
  - Budget dialog (`modules/budget.js`) and Goal dialog (`modules/goals.js`).

### 1.3 Add Expense (`expense.html`) — `dashboard.js` (shared script), `modules/split.js`, `modules/voice-command.js`

- **Purpose**: add an expense quickly and fix today's entries.
- **UI**:
  - Required fields:
    - **Amount** (with the currency symbol).
    - **Date**, with Today / Yesterday chips (`syncDateChips`).
    - **Category tile picker**, 12 tiles: Food, Groceries, Transport, Travel, Shopping, Bills,
      Home, Health, Fun, Education, Savings, Other. Each tile maps to a full category name (§3.1).
      Tiles are reordered by use in the last 60 days, once per visit (`orderCategoryTiles`).
  - **Description**.
  - **More details** section (collapsed; its summary shows the payment method):
    - Payment method: UPI, Cash, Credit Card, Debit Card, Net Banking, Other. Defaults to the last
      one used (`lastPayment`, `applyLastPayment`).
    - Card name, shown only for Credit/Debit Card (`toggleCardName`).
    - Notes.
    - **Repeats every month** switch. When on, it shows the EMI fields **Total payments** (2–600)
      and **Already paid** (`syncEmiFields`).
    - **Tags**, split on Enter or comma (`initTagInput`, `addTag`).
  - **Split with others** section: rows of name + optional email, up to 10, with a live summary
    (`split.js › addRow, updateSummary`).
  - Buttons **Clear** and **Add Expense**.
  - **Scan receipt** and voice FAB (`voice-command.js`), §7.4.
  - Below the form:
    - **Today** list of today's entries with edit/delete (`renderRecentExpenses`).
    - **EMIs** card (`renderEmis`).
    - Summary figures: month, year, all time, daily average (`updateExpenseCards`).
- **States**:
  - Inline messages in `#form-msg` (`showFormMsg`), validation listed in §2.3.
  - Duplicate warning (§2.3).
  - An undo toast for 6 s after saving (`showUndoToast`).
  - Empty Today list: "Nothing added today yet."
  - Offline: "Expense saved offline. Will sync when online."
- **Navigation**: the edit dialog (`openEditExpense`); after a split, the success message points to
  Debts.

### 1.4 Income (`income.html`) — `assets/js/income.js`

- **Purpose**: add income and see this month's total.
- **UI**:
  - Hero "{Month} so far": month total, "N entries · ₹Y this year" (`updateIncomeSummaryCards`).
  - Form:
    - **Amount**.
    - **Date**, with Today / Yesterday chips.
    - **From** (source), with chips for the 6 most-used sources. Tapping a chip also fills the
      bank last used with that source.
    - **Online / Cash** toggle (`setIncomePayment`).
    - **Bank or wallet**, only for Online, with suggestions from past entries.
    - **Notes**.
    - Buttons **Clear** and **Add income**.
  - **Recent**: the last 8 entries, tap to edit (`openIncomeEdit`), plus an "All income" link to
    `history.html#income`.
  - Edit dialog with a Delete button that asks for confirmation.
- **States**:
  - Empty: "No income yet. Add your first one above."
  - Error: toast "Error loading income data."
  - Offline: "Income saved offline. Will sync when online."
  - Pending rows are disabled and show "Waiting to sync".

### 1.5 Debts (`debt.html`) — `assets/js/debt.js`, `modules/split.js`, `modules/debt-data.js`

- **Purpose**: money lent and borrowed, optionally shared with the other person by email.
- **UI**:
  - **Net balance** hero (`updateDebtSummary`):
    - Amount with "in your favour / you owe overall / all square".
    - "N open debts with M people".
    - Tiles: Owed to you and You owe, each with an "N open" count.
  - **Add a debt** form (`addDebt`):
    - **They owe me / I owe them** toggle (`setDebtType`).
    - **Person**, with name suggestions (`modules/people.js`).
    - **Their email**, optional. It shares the debt with that person.
    - **Amount**, **Date** (with chips), **Notes**.
    - Buttons Clear and Add debt.
  - **By person** (`renderPeople`, `debt-data.js › debtsByPerson`):
    - Shown once there are 2 or more open debts.
    - Each row shows the net per person; tapping it filters the Open list.
  - **Open** list (`renderDebts`):
    - Each row: person, "Owes you / You owe · date · Shared / Added by them · claim text", the
      amount left, and a "₹X paid back" bar.
    - Actions depend on the row:
      - Own debt: **Got paid / I paid** (opens the payment dialog) and Delete.
      - Own debt with a pending claim: **Not yet / Confirm**.
      - Shared with me: **I paid / I got paid** (sends a claim), or **Undo** if a claim was already
        sent.
  - **Settled** list (`renderDebtHistory`):
    - Collapsed until opened: "Settled · N" with a total.
    - Own settled debts have **Reopen**.
  - **Payment dialog** (`openDebtSettleModal`, `fillPayDialog`, `payHint`):
    - Header: "₹X left of ₹Y" with a progress bar.
    - Fields: Amount (prefilled with what's left) and Date.
    - Live hint text, and a list of past payments, each with Remove (own debts only).
    - The button reads "Save", "Save and settle" or "Tell {person}".
  - Delete and Reopen confirmation dialogs.
- **States**:
  - Empty: "No open debts. Add one above when someone borrows or lends."
  - Error: toast "Error loading debt data."
  - `permission-denied` error: "SpendWise can't update this debt yet. Try again later." This is a
    rules problem, so the message does not blame the network.

### 1.6 History (`history.html`) — `dashboard.js › renderHistory, initHistory`, `modules/history-data.js`, `modules/csv-export.js`

- **Purpose**: one searchable list of every expense or income entry, plus export.
- **UI**:
  - Tabs: **Expenses / Income** (`hist-tabs`).
  - **Search**: plain words or `#tag`.
  - **Dates**: All time, This month, Last month, Last 3 months, This year, Custom (from/to).
  - **Sort**: Newest first, Oldest first, Largest first.
  - **Kind chips**: categories (Expenses) or sources (Income), most used first, multi-select.
    Hidden when there are fewer than 2.
  - **Tag chips**: tags used in the chosen dates.
  - Summary: "N expenses · **₹total** · Clear filters". When searching a tag, it also shows a
    per-category split.
  - List grouped by day with day totals: Today, Yesterday, "Mon, 19 Oct", plus the year if it's
    not this year (`historyDay`). Sorting by Largest drops the grouping.
  - "Show 50 more" button (`HISTORY_STEP = 50`).
  - Tapping a row opens the edit dialog: Expense (`openEditExpense`) or Income (`openIncEdit`).
  - **Export** dialog (§2.6).
- **States**:
  - Empty: "No expenses yet. Add an expense" / "No income recorded yet. Add income".
  - No results: "Nothing matches. Clear filters".
  - Pending rows are disabled and show "Waiting to sync".

### 1.7 Insights (`insights.html`) — `modules/insights-v2.js`, `modules/insights-data.js`, `modules/ask-data.js`

- **Purpose**: analysis only. It does not repeat the Dashboard (web `CLAUDE.md`, "No page repeats
  another").
- **UI**:
  1. **Ask about your spending**: a question box with example chips. The answer comes with a
     "see them in History" link (`askQuestion`, `renderAnswer`, `historyLink`), §6.9.
  2. **Forecast** (`renderForecast`):
     - Month-end total, compared with last month.
     - A stacked bar: spent / bills due / usual pace.
     - Next month's estimate.
  3. **Period tabs**: This month, Last month, 3 months, This year, Custom (`selectPeriod`,
     `showPeriod`).
  4. **Cards** (`renderGlance`), in this order:
     - Compared with {previous period} (`compareCard`).
     - Against your budget (`budgetCard`).
     - Where it went: a pie with up to 5 slices (`pieCard`).
     - What changed (`changedCard`).
     - Biggest expenses (`biggestCard`).
     - When you spend: weekdays, shown when there are 3 or more expenses (`weekdayCard`).
     - How you pay (`paymentCard`).
     - Fixed and flexible (`fixedCard`).
  5. **AI coach**: topic chips Overview, Spending, Savings, Trends, Debt (`TYPES`), then a chat.
     The chat is saved per user for 24 h (`saveChat`, `restoreChat`), and a consent dialog shows
     once (`ensureConsent`).
- **States**:
  - Loading skeleton (`setGlanceLoading`).
  - Error: "Couldn't load your data. Check your connection and try again." (`showError`).
  - Custom range errors: "Pick both a start and an end date." / "The start date is after the end
    date."
  - Empty: "Nothing recorded in {period}. Try a longer period, or add an expense."
  - Compare card with no prior data: "Nothing to compare yet…".
  - AI errors (`errorText`), including "Daily AI limit reached" (HTTP 429).

### 1.8 Investments (`invest.html`) — `assets/js/invest.js`, `modules/invest-data.js`

- **Purpose**: holdings (fund, FD, PPF, …), what they're worth, and the money put in.
- **UI**:
  - **All investments** hero (`renderHero`):
    - Worth.
    - "₹X put in · +₹G (+n.n%) gain".
    - "₹M put in this {Month} · ₹S a month in SIPs".
    - An "After debts" line, shown only when debts exist.
  - **Put money in** form (`addEntry`):
    - Holding chips plus "New holding".
    - Amount: the placeholder is the SIP amount when there is one, and an empty field uses the
      placeholder.
    - Date with chips.
    - Paid with: "Bank or auto-debit" (stored as `Net Banking`), UPI, Debit card, Cash, Other.
    - Notes.
    - **Every month (SIP)** switch, preset to on when the holding already has a SIP.
  - **Holdings** list (`renderHoldings`):
    - Each row: kind icon, name, "kind · ₹X a month · for {goal}", worth, and gain/loss.
    - A "Not in a holding" row for unmatched entries.
  - **Put in recently**: 10 rows, plus an "All in History" link.
  - **Holding dialog** (`openHolding`, `saveHolding`, `deleteHolding`):
    - Fields: Name (≤60), Kind, Current value (optional), Goal (shown only if goals exist).
    - Delete uses `confirm()`.
  - **Entry dialog** (`openEntry`, `saveEntry`, `deleteEntry`):
    - Fields: Into (holding, with "New holding…"), Amount, Date. Plus Delete.
- **States**:
  - Empty hero: "Add a holding below, then the money you put in."
  - Empty holdings: "A SIP, an FD, your PPF: add each one as a holding to see what it's worth."
  - Empty recent: "Nothing put in yet. Pick a holding above and add the first amount."
  - `permission-denied`: "Investments aren't switched on for this account yet. The app's security
    rules need an update."
  - Other errors: "Couldn't load your investments. Check your connection and reload."
  - Holdings can't be saved offline: "Holdings need a connection."

### 1.9 Settings (`settings.html`) — `assets/js/settings.js` and the modules named per row

- **UI**, in sections:
  - **Account**: avatar (photo, or the first letter of the name), name and email.
  - **Display**:
    - **Appearance**: System, Light or Dark; each tap moves to the next (`theme.js › toggleTheme`).
    - **Currency**: list of 35 (`utils/currency.js`).
  - **Money**: **Monthly budget** dialog (`budget.js`), §2.8.
  - **Notifications**: daily reminder switch and time (`reminders.js`), §7.5.
  - **Security**: app lock switch (`app-lock.js`), §7.7.
  - **Your data**:
    - **Import from a bank statement** (`csv-upload.js`), §7.4.
    - **Backup & restore** (`backup.js`), §7.8.
  - **App**: **Refresh the app** (`settings.js › refreshApp`, web only) and **Log out**.
- **States**: nothing loads on this page except through the dialogs.

---

## 2. User flows

### 2.1 Authentication — `dashboard.js › handleSignup, handleLogin, handleGoogleLogin, showForgotPassword/handleForgotPassword, onAuthStateChanged`; `page-shell.js › requireUser`

- **Sign up** (`handleSignup`):
  - All three fields are required: "Please fill in all fields."
  - Password rules, checked in this order:
    - At least 8 characters.
    - A lowercase letter, an uppercase letter, a digit.
    - A special character from `!@#$%^&*()_+-=[]{};':"\|,.<>/?`.
  - Then `createUserWithEmailAndPassword`, `updateProfile({displayName: name})`,
    `sendEmailVerification`, then **sign out**. The Login tab shows: "Account created! Please check
    your email to verify."
  - **No Firestore document is created for a user.** There is no `users` collection.
- **Login with email and password**: `signInWithEmailAndPassword`. If `!user.emailVerified`, the
  app signs out and shows "Please verify your email before logging in."
  - `index.html` applies this check to any provider (`dashboard.js › onAuthStateChanged`).
  - The other pages apply it only to the `password` provider (`page-shell.js › requireUser`).
- **Google**: `signInWithPopup(GoogleAuthProvider)`. Google accounts arrive verified. Android uses
  Credential Manager + `googleid`, then `GoogleAuthProvider.getCredential(idToken)`.
- **Forgot password**:
  - Validate the email: `/^[^@]+@[^@]+[.][^@]+/`.
  - `sendPasswordResetEmail`, then step 2 shows "We sent a password reset link to {email}…".
  - Errors: `auth/user-not-found`, `auth/invalid-email`, `auth/too-many-requests`, otherwise a
    connection message.
- **Error copy** (`helpers.js › friendlyErr`): reuse it as is for Firebase Auth error codes.
- **Log out** (`page-shell.js › installMenus`): clears the cached lists, then `signOut`. Other pages
  go to `index.html`.
- **Verified email matters for data**: sharing a debt by email, and reading debts shared with you,
  both require `email_verified == true` in the token (§4).

### 2.2 Loading data (all screens)

- Each collection is read with one query, `collection(X).where('uid','==',uid)`, and sorted by
  `date` descending on the client.
- Debts also run `where('withEmail','==', user.email.toLowerCase())`, only when the email is
  verified, through `split.js › fetchSharedDebts`.
  - Each shared result is mapped to the viewer's side with `helpers.js › sharedDebtView`.
  - Failures are swallowed: "Shared debts unavailable".
- The web keeps a sessionStorage copy per user and kind (`dashboard.js › saveToCache,
  loadFromCache`). Android can use Firestore's own cache instead (Q10).

### 2.3 Expenses

- **Add** (`dashboard.js › addExpense`). Validation, in order:
  1. amount > 0: "Enter a valid amount."
  2. Total payments, if given, is an integer from 2 to 600: "Total payments should be a whole
     number from 2 to 600."
  3. Already paid, if given, is an integer ≥ 0 and below the total. Errors: "Already paid should be
     a whole number below {N}, counting this payment out." / "Already paid needs Total payments
     too." / "Add Total payments for this EMI."
  4. Category chosen: "Select a category."
  5. Date chosen: "Select a date."
  6. Split rows are valid (§2.4).
- **Before saving**:
  - Trim and sanitize text (`helpers.js › sanitize`): strip control characters, cap length.
  - Limits: description ≤300, notes ≤500, card name ≤100.
  - An empty description becomes `"-"`.
- **Double-tap guard**: the same `amount|category|date` within 2 minutes asks once: "You added ₹X
  for {cat} a moment ago. Tap Add Expense again to save it twice."
- **Write**: one `writeBatch` containing the expense plus one debt per split person. Then:
  - `announceDebts`, which POSTs to the Worker `/push/debt` (§7.1).
  - The form resets; the payment method is remembered.
  - Toast "Expense added. **Undo**". Undo deletes the expense and its split debts.
  - Calls the Worker's `/push/logged` (`reminders.js › markLoggedToday`).
- **Offline**: queued (§7.3). The row shows "waiting to sync" and can't be edited.
- **Edit** (`openEditExpense`, `saveEditExpense`):
  - Same validation.
  - `updateDoc` with amount, category, date, payment, cardName, description, notes, tags,
    `encoding: "plain"`.
  - The repeat switch: turning it on writes `repeat: "monthly"`; turning off a `monthly` entry
    writes `repeat: "stopped"`; otherwise the old value is kept.
  - The edit dialog **does not** change `emiTotal`, `emiPaidBefore` or `holdingId`.
  - Pending (offline) entries can't be edited.
- **Delete** (`deleteExpense`, `confirmDelete`): a confirmation dialog, then `deleteDoc`. It is also
  reachable from the edit dialog (`deleteFromEdit`).
- **Due bills on the Dashboard** (`dashboard.js › addDue` and its click handler):
  - **Add** writes a copy of the template:
    - Fields copied: amount, category, payment, cardName, description, tags, `repeat: "monthly"`,
      `holdingId`, `emiTotal`, `emiPaidBefore`.
    - `date` = the due date and `notes` = `""`.
  - **Skip month** sets device-local `localStorage['spendwise_skip:'+key+':'+YYYY-MM] = '1'`
    (`helpers.js › SKIP_KEY, isSkippedBill`). **This is not synced.**
  - **Stop repeating** runs `updateDoc(template, {repeat: "stopped"})`. Toast: "It won't be
    suggested again. Past entries stay."

### 2.4 Split an expense — `split.js › getSplit, splitDebts`, `helpers.js › splitAmount`

- **Validation**:
  - Each row needs a name: "Enter a name for each person you split with."
  - An email, if given, must be valid (`split.js › emailError`):
    - Regex `/^[^@\s]+@[^@\s]+\.[^@\s]+$/`.
    - It can't be your own email: "That's your own email. Use the other person's."
  - Maximum 10 people.
- **Saving**:
  - The expense amount becomes your share.
  - Each person gets a debt with `type: 'they-owe'`, `amount: each` and `notes: "Split: {description or category}"`.
  - If they have an email, the debt also gets the share fields (§3.3).

### 2.5 Income — `income.js › addIncome, saveEditIncome, confirmIncomeDelete`; `dashboard.js › saveIncEdit, confirmIncDelete` (History)

- **Validation**:
  - amount > 0: "Enter a valid amount."
  - Date: "Select a date."
  - Source: "Enter income source / from."
  - Online requires a bank: "Enter bank/wallet name for online payments."
- **Limits**: source ≤100, bank ≤100, notes ≤500. Bank is `''` for Cash.
- **Write**: `addDoc`. Edit uses `updateDoc` with `{amount, date, source, paymentType, bank, notes, encoding:'plain'}`.
  Delete asks for confirmation first.

### 2.6 History filters and CSV export — `history-data.js`, `csv-export.js`, `dashboard.js › exportEntries, updateExportDialog, downloadHistoryExport`

- **Filters**:
  - Date range keys come from `historyRange` (§6.10).
  - Kinds are categories or sources. Tags come from `tagsInUse`.
  - Search (`filterHistory`):
    - `#tag` matches that tag exactly, case-insensitive.
    - Otherwise **every** word must appear in some field.
    - Expense fields searched: description, category, notes, payment, cardName, tags.
    - Income fields searched: source, bank, notes, paymentType.
- **Export dialog**:
  - Its own date range, which starts as History's.
  - Options:
    - "What's shown": the current tab with chips and search applied.
    - "Expenses and income": both, ignoring search and chips.
  - A "Total" checkbox adds total rows.
  - Options with nothing in them are disabled; when both are empty it shows `#export-empty`.
- **File**:
  - `buildCsv` writes UTF-8 **with BOM** and **CRLF** line endings.
  - Columns: `Date, Type, Category or source, Description, Payment, Card or bank, Tags, Notes, Amount ({currency code})`.
  - Amounts use `toFixed(2)`. A description of `-` exports as empty.
  - Tags export as `#a #b`.
  - Cells starting with `= + - @ \t \r` get a leading `'` (formula defusing). Cells are quoted only
    when they contain `" , \r \n`.
  - With Total checked: a blank line, then "Total spent", "Total income" and "Net" (Net only when
    both kinds are present).
- **Name**: `exportFileName` gives `SpendWise_{Expenses|Income|Expenses-and-income}_{from|start}_to_{to|today}.csv`,
  or `SpendWise_{what}_all-time_{today}.csv`.
- **Tests to port**: `tests/csv-export.test.mjs`.

### 2.7 Debts — `debt.js`, `split.js`, `helpers.js › payDebt, debtClaim, debtLeft`

- **Add**:
  - Validation:
    - amount > 0: "Enter a valid amount."
    - Person: "Enter person name." (≤100)
    - Date: "Select a date."
    - Email: `emailError`.
  - Write: `addDoc` with the §3.3 fields. With an email, also `/push/debt`.
- **Record a payment** on your own debt (`confirmDebtSettle`):
  - `payDebt(d, amount, date)` returns a new `paid` list, sorted oldest first. Errors: "Enter the
    amount paid." / "That's more than the ₹X left."
  - Partial payment: `updateDoc({paid})`.
  - Paying everything left: `updateDoc({paid, settled:true, settledDate:date, claimDate: deleteField()})`.
- **Remove a payment**: `updateDoc({paid: list without that item})`.
- **Shared debt, from the other person's side** (`sendClaim`, `split.js › claimSettled`):
  `updateDoc({claimDate, claimAmount})`, then `/push/claim`. **Undo** runs
  `withdrawClaim`, which deletes both fields.
- **Owner answers a claim** (`split.js › confirmClaim, rejectClaim`):
  - **Confirm** runs `payDebt` with the claimed amount and date, then
    `updateDoc({paid, [settled:true, settledDate], claimDate: deleteField(), claimAmount: deleteField()})`.
  - **Not yet** deletes the claim fields.
- **Delete**: owner only, after confirmation.
- **Reopen** (`confirmDebtReactivate`): `updateDoc({settled:false, settledDate:''})`. If the debt
  was paid off through payments (`debtLeft == 0` and `paid` is non-empty), the **last payment is
  removed** as well.

### 2.8 Budget and goals (stored in `settings/{uid}`) — `budget.js`, `goals.js`, `modules/settings.js › saveSettings`

- **Budget dialog**:
  - **Monthly limit**, plus optional per-category limits for the 11 categories that aren't
    Savings & Investments. Number parsing: `num()` strips non-digits and rounds to 2 places.
  - Buttons:
    - **Save** writes `{budget:{total, cats}}`.
    - **Remove budget** writes `{budget:{total:null, cats:{}}}`.
    - **Suggest from my income** (§6.8).
- **Goal dialog**:
  - Fields:
    - "What it's for" (≤40).
    - Amount (target).
    - By (month, `YYYY-MM`).
    - Saved so far.
  - Add-money mode adds to `saved`.
  - Delete.
  - The whole `goals` list is rewritten on every change; maximum 20 goals.
  - A goal that reaches its target is celebrated once.
- **Writes**: `setDoc(settings/uid, fields, {mergeFields: Object.keys(fields)})`, so other fields
  are left alone.

### 2.9 Investments — `invest.js`

- **Put money in**: writes an **expense** with these fields:
  - `category:'Savings & Investments'`, `description: holding.name`, `holdingId`.
  - `tags: []`, `cardName: ''`.
  - `repeat:'monthly'` if the SIP switch is on. If the switch is off and the holding had a SIP,
    `repeat:'stopped'`.
- **Holding save**:
  - Duplicate names are refused, case-insensitively.
  - A changed value gets `valueDate = today`; an unchanged value keeps its date.
  - Clearing the value deletes `value` and `valueDate`. Clearing the goal deletes `goalId`.
- **Holding delete**: first sets its SIP template to `repeat:'stopped'`, then deletes the holding.
  Its entries keep their `holdingId` and description.
- **Entry edit**:
  - Writes `{amount, date}` plus, if moved into a holding, `{holdingId, description: holding.name}`.
  - Moving it out of a holding deletes `holdingId`.

### 2.10 Tabbed time views (Dashboard "Spending over time") — `dashboard.js › renderDashboardTable (override), getTrendData, getPeriodExpenses, updatePeriodSummary, getWeekStart`

Tab ranges. The table includes investments; the chart and summary exclude them.

| Tab | Table rows | Chart |
|---|---|---|
| Daily | expenses dated today (within the picker's month/year) | last 7 days, labels like "Mon 26", **ignores the picker** |
| Weekly | from Monday of this week to today | last 4 Monday–Sunday weeks, labels like "9–15 Mar" or "28 Mar–3 Apr", ignores the picker |
| Monthly | the picker's month and year (default: now) | 12 months of the picker's year |
| Yearly | the picker's year (month ignored) | every year that has data |

- The picker defaults to the current month and year. Options include "All Months" and "All Years".
- **Weeks start on Monday** (`getWeekStart`).

### 2.11 Theme switching — `assets/js/theme.js`

- Three modes: `system` (default; the `theme` key is absent), `light`, `dark`.
- Each tap moves system → light → dark → system.
- Stored in `localStorage['theme']`, per device. System mode follows OS changes live.
- Android: DataStore, same three modes (matches `CLAUDE.md`).

### 2.12 Currency — `utils/currency.js`

- `localStorage['spendwise_currency']`, default `INR`, per device.
- Display only: amounts are never converted or tagged with a currency.
- Formats:
  - `fmt`: `symbol + ' ' + toLocaleString(locale, 2 decimals)`.
  - `moneyWhole`: `symbol + ' ' + rounded toLocaleString(locale)`.
  - `fmtCompact`: Cr/L/k suffixes.
- 35 currencies, each with code, symbol, name and locale: copy the `CURRENCIES` table verbatim.

---

## 3. Firestore schema

There are 5 top-level collections and **no subcollections**. Document ids are Firestore auto-ids,
except in these cases:

- **Offline-queued writes** use the queue id: `Date.now().toString(36) + 9 random base-36 characters`
  (`offline-queue.js › generateId`).
- **Restored backups** keep the exported id (`backup.js`).
- **`settings`** uses the user's uid as its id.

The examples below are **reconstructed from the write code** with illustrative values. The field
tables were **checked against a real backup export on 2026-10-26** (748 expenses, 18 income,
7 debts, 3 holdings; no settings). §3.9 lists what that data showed.

### 3.0 Conventions (all collections)

| Thing | Format | Source |
|---|---|---|
| Calendar dates (`date`, `settledDate`, `claimDate`, `valueDate`, `paid[].date`) | string `YYYY-MM-DD` in the **user's local calendar**. Never derive it from UTC. | `helpers.js › todayStr` |
| Months (`goals[].by`) | string `YYYY-MM` | `goals.js` |
| Time of day (`remindAt`) | string `HH:MM`, 24h | `reminders.js` |
| `createdAt` | Firestore `Timestamp` from `serverTimestamp()`. Offline-queued items get it when they sync. Not used for ordering, except in the notification feed. | every write |
| Amounts | JS `number`. The JS SDK stores a whole number as **int64** and a fraction as **double**, so `amount` holds both types. Values are rounded to 2 places where code rounds (`payDebt`, `num()`). | — |
| Currency | **not stored**. A per-device display setting (§2.12). | `currency.js` |
| `uid` | owner's Firebase uid; required, and checked by the rules | `firestore.rules` |
| `encoding` | `"plain"` on writes since 2026-03-15. Absent on older records (§5) and on income imported from CSV. | `dashboard.js › addExpense` etc. |

### 3.1 `expenses/{id}`

Written by:
- `dashboard.js › addExpense, saveEditExpense, addDue`
- `invest.js › addEntry, saveEntry`
- `voice-command.js › toDocData`
- `csv-upload.js › saveToFirestore`
- `backup.js › importData`
- `offline-queue.js › syncOfflineQueue`

| Field | Type | Required | Notes |
|---|---|---|---|
| `uid` | string | yes | |
| `amount` | number (int64 or double) | yes | > 0. For a split, this is your share. Legacy: may be a string (§3.7). |
| `category` | string | yes | One of: `Food & Dining`, `Groceries`, `Transport`, `Travel`, `Shopping`, `Bills & Utilities`, `Home & Rent`, `Health`, `Entertainment`, `Education`, `Savings & Investments`, `Other` (`helpers.js › EXPENSE_CATEGORIES`). Old names are read through aliases: `Subscription`→Bills & Utilities, `Personal Care`→Shopping, `Gifts`→Other, `Rent`→Home & Rent, `Investment`→Savings & Investments (`CATEGORY_ALIASES`, `normalizeCategory`). Aliased values are never rewritten, except when the entry is edited. |
| `date` | string `YYYY-MM-DD` | yes | |
| `payment` | string | yes | `UPI`, `Cash`, `Credit Card`, `Debit Card`, `Net Banking`, `Other`. CSV import may write whatever the AI returned. |
| `cardName` | string | yes (may be `""`) | Only set for Credit/Debit Card; ≤100 |
| `description` | string | yes | `"-"` when empty. ≤300 from forms. Equals the holding's name for investment entries. |
| `notes` | string | yes (may be `""`) | ≤500 |
| `tags` | array\<string\> | form writes always include it; **absent** in CSV imports | No `#`, case kept, no duplicates. Voice entry: ≤10 tags of ≤40 characters (`voice-data.js › parseTags`). |
| `encoding` | string `"plain"` | see §3.0 | |
| `createdAt` | Timestamp | yes | |
| `repeat` | string | optional | `"monthly"` starts or continues a series; `"stopped"` ends it. |
| `emiTotal` | integer 2–600 | optional | Only with `repeat`. Copied on each Due → Add. |
| `emiPaidBefore` | integer ≥0, < emiTotal | optional | Omitted when 0 |
| `holdingId` | string | optional | A `holdings` doc id. Only on Savings & Investments entries. |

```json
// expenses/Xq3d9VfK2mLr8TzP1aBc — Add Expense form, a repeating EMI
{
  "uid": "pR4nT0q8vUeZ3yW1sX7kLm2N9oJ5",
  "amount": 4250,                 // int64 (whole number)
  "category": "Bills & Utilities",
  "date": "2026-10-05",
  "payment": "Credit Card",
  "cardName": "HDFC Millennia",
  "description": "Phone EMI",
  "notes": "",
  "tags": ["iphone"],
  "encoding": "plain",
  "createdAt": Timestamp(2026-10-05T09:12:44Z),
  "repeat": "monthly",
  "emiTotal": 12,
  "emiPaidBefore": 3
}
// expenses/{id} — Investments page, a SIP into a holding
{ "uid": "…", "amount": 5000, "category": "Savings & Investments", "date": "2026-10-10",
  "payment": "Net Banking", "cardName": "", "description": "Parag Parikh Flexi Cap",
  "notes": "", "tags": [], "holdingId": "h7Gk2LpQ9sWx", "encoding": "plain",
  "repeat": "monthly", "createdAt": Timestamp(…) }
// expenses/{id} — a one-off with a decimal amount
{ "uid": "…", "amount": 182.5, "category": "Food & Dining", "date": "2026-10-24",
  "payment": "UPI", "cardName": "", "description": "-", "notes": "", "tags": [],
  "encoding": "plain", "createdAt": Timestamp(…) }
```

**Repeating series semantics** (`helpers.js` "Repeating expenses" block):
- A series is every expense with the same `recurringKey`:
  `normalizeCategory(category) + '|' + description.trim().toLowerCase()`.
- The **template** is the newest entry (by `date`) that **has** a `repeat` field.
- The series is active when the template's `repeat === 'monthly'`.
- EMI progress: `paid = min(entriesInSeries + emiPaidBefore, emiTotal)`.

### 3.2 `income/{id}`

Written by:
- `income.js › addIncome, saveEditIncome`
- `dashboard.js › saveIncEdit`
- `voice-command.js › toDocData`
- `csv-upload.js`
- `backup.js`
- the offline queue

| Field | Type | Required | Notes |
|---|---|---|---|
| `uid` | string | yes | |
| `amount` | number | yes | > 0 |
| `date` | string `YYYY-MM-DD` | yes | |
| `source` | string | yes | ≤100. Who paid ("Salary", "Acme Corp"). CSV import puts the description here. |
| `paymentType` | string | yes | `"Online"` or `"Cash"` |
| `bank` | string | yes (may be `""`) | `""` when Cash; ≤100 |
| `notes` | string | yes (may be `""`) | ≤500 |
| `encoding` | `"plain"` | absent on CSV imports | |
| `createdAt` | Timestamp | yes | |

```json
// income/Lk2P9qWm3XzR7aB0cD1e
{ "uid": "…", "amount": 85000, "date": "2026-10-01", "source": "Salary",
  "paymentType": "Online", "bank": "ICICI", "notes": "", "encoding": "plain",
  "createdAt": Timestamp(…) }
```

### 3.3 `debts/{id}`

Written by:
- `debt.js › addDebt, confirmDebtSettle, confirmDebtReactivate`, plus the payment-remove handler
- `split.js › splitDebts, claimSettled, withdrawClaim, confirmClaim`
- `voice-command.js`
- `backup.js`
- the offline queue

| Field | Type | Required | Notes |
|---|---|---|---|
| `uid` | string | yes | The person who recorded it |
| `amount` | number | yes | The original amount. Never reduced by payments. |
| `type` | string | yes | `"they-owe"` (they owe the owner) or `"i-owe"`. Seen from the owner's side. |
| `person` | string | yes | ≤100 |
| `date` | string `YYYY-MM-DD` | yes | |
| `notes` | string | yes (may be `""`) | Split debts: `"Split: {label}"` |
| `settled` | boolean | yes | Legacy: `"true"` or `1` (§3.7) |
| `settledDate` | string `YYYY-MM-DD` or `""` | yes | |
| `encoding` | `"plain"` | yes | |
| `createdAt` | Timestamp | yes | The Worker requires it to be under 1 h old for `/push/debt` |
| `paid` | array\<map{`amount`: number, `date`: string}\> | optional | Part-payments, oldest first |
| `withEmail` | string, lowercase, ≤254 | optional (share) | The other person's email. Must not be your own. |
| `fromEmail` | string | with `withEmail` | Your verified email, lowercase. The rules require it to equal the token's. |
| `fromName` | string ≤100 | with `withEmail` | `displayName` or email |
| `claimDate` | string `YYYY-MM-DD` | optional | Set by the `withEmail` person: "I paid / I got paid" |
| `claimAmount` | number > 0, ≤ `amount` | optional | Missing on old claims, which then mean "all that's left" (`debtClaim`) |

On new writes, the three share fields (`withEmail`, `fromEmail`, `fromName`) are all present or
all absent, and they can't change after creation (§4). **Real data breaks the "all present" part:**
one shared debt has `withEmail` and `fromName` but no `fromEmail` (§3.9). So:

- Readers must treat `fromEmail` as optional.
- An owner's update must change only the fields it means to change: `update(...)` with field
  paths, never `set` over the whole document. This keeps the share fields byte-for-byte untouched,
  as the rules require.

```json
// debts/Rt5Yh8Jk2Lm4Np6Qr8St — a split share, shared by email, half paid, with a pending claim
{
  "uid": "pR4nT0q8vUeZ3yW1sX7kLm2N9oJ5",
  "amount": 600,
  "type": "they-owe",
  "person": "Ravi",
  "date": "2026-10-12",
  "notes": "Split: Dinner at Toit",
  "settled": false,
  "settledDate": "",
  "encoding": "plain",
  "createdAt": Timestamp(…),
  "withEmail": "ravi@example.com",
  "fromName": "Shuvrasankha Paul",
  "fromEmail": "owner@example.com",
  "paid": [{ "amount": 300, "date": "2026-10-15" }],
  "claimDate": "2026-10-20",
  "claimAmount": 300
}
// debts/{id} — a private debt, settled
{ "uid": "…", "amount": 2000, "type": "i-owe", "person": "Mom", "date": "2026-09-02",
  "notes": "", "settled": true, "settledDate": "2026-09-30", "encoding": "plain",
  "createdAt": Timestamp(…) }
```

**The other person's view** (`helpers.js › sharedDebtView`):

- `type` is flipped (`they-owe` ↔ `i-owe`).
- `person = fromName || 'Someone'` and `email = fromEmail`.
- The row is flagged `shared: true` and is read-only, except for the claim fields.

### 3.4 `holdings/{id}` (strict schema, enforced by the rules)

Written by `invest.js › saveHolding, deleteHolding` and `backup.js`. **Only these keys are allowed**
(`firestore.rules › validHolding`):

| Field | Type | Required | Notes |
|---|---|---|---|
| `uid` | string | yes | |
| `name` | string, 1–60 chars | yes | Unique per user, case-insensitive (checked by the client only) |
| `kind` | string | yes | `mf`, `stocks`, `fd`, `rd`, `ppf`, `nps`, `epf`, `gold`, `other` (`invest-data.js › HOLDING_KINDS` gives labels and icons) |
| `value` | number ≥0 | optional | Worth as of `valueDate`; set together with it |
| `valueDate` | string `YYYY-MM-DD` | optional | |
| `goalId` | string ≤40 | optional | A `settings.goals[].id` |
| `createdAt` | Timestamp | create | |

```json
// holdings/h7Gk2LpQ9sWx
{ "uid": "…", "name": "Parag Parikh Flexi Cap", "kind": "mf", "value": 132400,
  "valueDate": "2026-10-01", "goalId": "m2x9k1a", "createdAt": Timestamp(…) }
```

### 3.5 `settings/{uid}` (one per user; strict schema, enforced by the rules)

Written by `modules/settings.js › saveSettings` with `mergeFields`. **Only these keys are allowed**
(`firestore.rules › validSettings`):

| Field | Type | Notes |
|---|---|---|
| `budget` | map `{ total: number \| null, cats: map<categoryName, number> }` | `total:null, cats:{}` means no budget. Category keys are the full names from §3.1. (`budget.js`) |
| `goals` | array (≤20) of map `{ id: string, name: string ≤40, target: number, by: "YYYY-MM", saved: number }` | `id = Date.now().toString(36)` (`goals.js › save`). Readers drop invalid items (`getGoals`). |
| `remindAt` | string `HH:MM` (rule regex `^[0-2][0-9]:[0-5][0-9]$`) | Daily reminder time (`reminders.js`) |
| `remindOn` | bool | Reminder switch for the account |
| `notif` | map `{ seen: array<string> ≤200, cleared: array<string> ≤200 }` | Bell item ids (§7.6), newest last (`helpers.js › mergeIds`) |

```json
// settings/pR4nT0q8vUeZ3yW1sX7kLm2N9oJ5
{
  "budget": { "total": 40000, "cats": { "Food & Dining": 8000, "Shopping": 5000 } },
  "goals": [ { "id": "m2x9k1a", "name": "Goa trip", "target": 30000, "by": "2027-03", "saved": 6500 } ],
  "remindAt": "21:00",
  "remindOn": true,
  "notif": { "seen": ["Rt5Yh8Jk2Lm4Np6Qr8St:split"], "cleared": [] }
}
```

> **Do not** add any other field to `settings` (for example `currency` or `theme`) or to
> `holdings`. The write would be rejected (Q5).

### 3.6 Not in Firestore

These live in the Worker's Cloudflare D1 database (`worker/schema.sql`), not in Firestore:

- Push subscriptions, reminder state, AI usage, bill-due rows and month totals.

These are device-local in the web app (localStorage or sessionStorage):

- Theme, currency, last payment method (`spendwise_last_payment`), skipped bills
  (`spendwise_skip:*`), the offline queue (`spendwise_offline_queue`), the AI chat (24 h), AI
  consent (`aiAdvisorConsentV3`), app lock, and data caches.

### 3.7 Legacy values readers must accept (`dashboard.js › loadExpenses, loadDebts`; `invest.js › load`; `insights-v2.js › loadData`)

- **`amount`**: read as `parseFloat(raw.amount)`, and NaN becomes 0. It can be:
  - a number (current writes);
  - a numeric string such as `"250"` (expenses written 2026-03-13 to 03-15);
  - an XOR/base64 string (expenses written 2026-03-03 to 03-13, §5). **None of these exist in
    production** (§3.9), but the 0 fallback still covers them.
  Kotlin equivalent: `(raw as? Number)?.toDouble() ?: (raw as? String)?.toDoubleOrNull() ?: 0.0`.
  JS `parseFloat` also accepts a leading-number prefix such as `"12abc"`. None of the computed
  vectors hit that case.
- **`settled`**: `true`, `"true"` and `1` all mean settled.
- **Missing fields** default to empty: `description ""`, `notes ""`, `cardName ""`, `tags []`,
  `settledDate ""`, `paid []`, `category → 'Other'` through `normalizeCategory`.

### 3.8 Writing from Android so the web reads it

- Write the same field set as the matching web path, **including** `encoding: "plain"`, `tags`
  (even when empty), `description: "-"` when empty, and `createdAt = FieldValue.serverTimestamp()`.
- Amounts: either Long or Double reads fine on the web. To match web data exactly, write a `Long`
  when the value is whole and a `Double` otherwise (Q7).
- Store `date` from `LocalDate.now(ZoneId.systemDefault())`. Never derive it from UTC.

### 3.9 Observed in production (backup export, 2026-10-26)

The export's `id` is the doc id, not a stored field. `createdAt` is exported as
`{seconds, nanoseconds}`, and is a `Timestamp` in Firestore.

- **expenses (748)**
  - `amount`: 746 whole numbers, 1 fraction, and 1 numeric **string**. The string doc is from
    2026-03-14 and is the only one without `encoding`.
  - The other 747 have `encoding: "plain"`.
  - `tags` is present on 458 docs and absent on older ones.
  - `cardName` is present on 741 and **absent on 7**, so read it as optional (default `""`).
  - `holdingId` is on 6 docs; `repeat` on 4 (`monthly`, `stopped`).
  - No `emiTotal` or `emiPaidBefore` was seen.
  - Categories seen include the alias names `Investment`, `Subscription`, `Personal Care`,
    `Gifts` and `Rent`. These are all covered by `CATEGORY_ALIASES`, and no unknown category was
    seen.
- **income (18)**
  - `amount`: all whole numbers.
  - `paymentType`: only `"Online"` seen.
  - `encoding` is present on 10 and **absent on 8** (CSV imports), so never require it.
- **debts (7)**
  - `amount`: whole numbers. `type`: only `"they-owe"`. `settled`: real booleans.
  - `settledDate` is `""` when unsettled.
  - `withEmail` is on 6, `fromName` on 6, `fromEmail` on **5** (see §3.3).
  - No `paid`, `claimDate` or `claimAmount` was seen.
- **holdings (3)**: `uid`, `name`, `kind` (`mf`, `other`) and `createdAt`. No `value`, `valueDate`
  or `goalId` was seen.
- **settings**: not in the export, so §3.5 stands as written from the rules.
- **Int vs double**: JSON can't tell a whole-number double from an int64, so the export doesn't
  settle it. Read `amount` as any `Number` (Long or Double) or a numeric `String`. Write it per Q7.

---

## 4. Security rules (`firestore.rules`)

- **Publishing**: the rules are published **by hand** in the Firebase console, and the file is the
  source of truth (web `CLAUDE.md`). The Android app can't change them; any change goes through the
  web repo and its owner.
- **Helpers**:
  - `owns()`: `auth.uid == resource.data.uid`.
  - `createsOwn()`: `auth.uid == request.resource.data.uid`.
  - `keepsOwner()`: `uid` can't change.
  - `emailVerified()`: `token.email_verified == true`.
  - `myEmail()`: `token.email.lower()`.

### 4.1 Rules per collection

| Collection | Read | Create | Update | Delete |
|---|---|---|---|---|
| `expenses`, `income` | owner | `uid` is the caller | owner, uid unchanged | owner |
| `holdings` | owner | `uid` is the caller **and** `validHolding` | owner, uid unchanged, `validHolding` | owner |
| `settings/{uid}` | doc id == caller | doc id == caller **and** `validSettings` | same | doc id == caller |
| `debts` | owner, **or** verified email == `withEmail` | `uid` is the caller **and** `validShare` | (a) owner, uid unchanged, `withEmail`/`fromEmail`/`fromName` untouched; **or** (b) the `withEmail` person on an unsettled debt, changing **only** `claimDate` (`YYYY-MM-DD` regex) and/or `claimAmount` (number, >0, ≤ `amount`) | owner |

### 4.2 `validShare(d)`

Either none of `withEmail`, `fromEmail`, `fromName` is present, or all of these hold:

- The caller's email is verified.
- `withEmail` is a lowercase string of ≤254 characters and is not the caller's email.
- `fromEmail == myEmail()`.
- `fromName` is a string of ≤100 characters.

### 4.3 What Android must respect

1. **Always** query with `whereEqualTo("uid", uid)`. A query without it fails, because Firestore
   rejects queries the rules can't prove safe. Shared debts use
   `whereEqualTo("withEmail", email.lowercase())`.
2. Never write another `uid`, and never change `uid` on update.
3. Lowercase `withEmail` and `fromEmail`. Set `fromEmail` from the **token's** email. Only offer
   sharing when `user.isEmailVerified`.
4. You can't add or remove sharing on an existing debt. Only the creator can.
5. As the `withEmail` person, update **only** `claimDate` and `claimAmount`, and only while the
   debt is unsettled. Use `FieldValue.delete()` to withdraw. Any other field gives
   `PERMISSION_DENIED`.
6. `holdings` and `settings` are strict: no extra keys, values in range (goals ≤20, notif lists
   ≤200, `remindAt` format, `kind` from the list, `name` 1–60).
7. Use `SetOptions.mergeFields(...)` for `settings` so other fields are untouched.
   `validSettings` checks the resulting document.
8. **Treat `PERMISSION_DENIED` as a rules problem**, not a network problem. The web copy says
   "…can't update this debt yet. Try again later." (`debt.js`).
9. Rules **don't validate** most fields on `expenses`, `income` and `debts`. Validation is the
   client's job, so Android must apply the §2 checks itself.

---

## 5. Encryption

**Android decision (Q1):** plain values only, and no crypto class. The legacy material below is
kept in case a read-only decoder is added later.

**Current state: no field is encrypted.** All writes since 2026-03-15 store plain values with
`encoding: "plain"` (`dashboard.js › addExpense`, `income.js › addIncome`, `debt.js › addDebt`).
The only crypto in the codebase is unrelated to stored data:

- Web Push payload encryption, RFC 8291 (`worker/src/webpush.js › encryptPush`).
- WebAuthn app lock (`modules/app-lock.js`).

**Legacy history** (from git):

| Period | Commit(s) | What was stored |
|---|---|---|
| 2026-03-03 → 2026-03-13 | `d0183ca`, `1165882` (`app.js › enc`) | **expenses only**: `amount = enc(String(amount))`, `description = enc(description \|\| "-")`, `notes = enc(notes)`. Category, date and payment were plain. Income and debts didn't exist yet. |
| 2026-03-13 → 2026-03-15 | `ec42284` | Plain, but `amount` stored as a **string**. `dec()` still applied on read. |
| 2026-03-15 → 2026-04-03 | `335cdf0` | Plain number, `encoding: "plain"`. `dec()` applied only when `encoding !== "plain"`. |
| 2026-04-03 → now | `509a399` | The decoder is a no-op. Legacy records show raw base64 text and amount 0 on the web. |

**Algorithm (legacy, not real encryption)**: repeating-key XOR, then base64.

- Key: the ASCII string `"SpendWise_2024_K"` (16 bytes).
- `enc(s)`: for each UTF-16 code unit `s[i]`, output `charCode(s[i]) XOR charCode(KEY[i % 16])`.
  Then `btoa` the result: standard base64 with `=` padding, over Latin-1 bytes.
- `dec(b)`: `atob(b)`, XOR with the same key. If `atob` throws (input isn't valid base64), return
  the input unchanged.
- There is **no key derivation**: the key is a constant that was hard-coded in the client.
- `enc` **threw** (`InvalidCharacterError`) for characters above U+00FF, such as `₹`. Legacy
  records therefore contain only Latin-1 text.

**Test vectors**: computed by running the original `enc` and `dec` from `git show ec42284^:app.js`
in Node.

| Input (plain) | `enc` output |
|---|---|
| `"250"` | `"YUVV"` |
| `"1499.5"` | `"YkRcV0pi"` |
| `"0.75"` | `"Y15SWw=="` |
| `"100000"` | `"YkBVXlRn"` |
| `"-"` | `"fg=="` |
| `""` | `""` |
| `"Lunch at office"` | `"HwULDQx3CAdFMFRWW1c6"` |
| `"Swiggy dinner with friends"` | `"AAcMCQMuSRcMMVxVQBQoIicYRQgWPgwdASw="` |
| `"Paid via GPay"` | `"AxEMCkQhABJFGGJRSw=="` |

**Negative vectors** (why decoding everything is unsafe):

- `dec("Coffee")` = `"Y÷º\u0017"` and `dec("250")` = `"í"`. Valid-looking base64 plain text
  decodes to garbage.
- `dec("Groceries")` = `"Groceries"`: not valid base64, so it's returned unchanged.
- `parseFloat` of every encoded amount above is `NaN`, which the web shows as **0**.

A safe detector for a legacy record:

1. `encoding` is absent;
2. `createdAt` is ≤ 2026-03-13T23:59:59+05:30;
3. `amount` is a string that isn't numeric.

Even then, decode only `amount`, `description` and `notes`. See Q1.

---

## 6. Business logic (port to `domain/`, pure Kotlin)

The web keeps pure logic in `utils/helpers.js` and `modules/*-data.js`, with Node tests in
`tests/*.test.mjs`. **Port those tests as Kotlin unit tests**: they are ready-made vectors.

All dates are local `YYYY-MM-DD` strings compared as text. **Spending** means every expense except
Savings & Investments (`helpers.js › isSpending`). Investments count as saved, never as spent, in
every total, chart and projection.

### 6.1 Core helpers (`utils/helpers.js`)

- `normalizeCategory(c)` = `ALIASES[c.trim()] ?: c.trim().ifEmpty("Other")`.
- `budgetState(spent, limit)`:
  - null if `limit ≤ 0`.
  - `ratio = spent/limit`, `left = limit − spent`.
  - state: `over` if ratio > 1, `near` if ratio ≥ 0.8, else `ok`.
- `goalPace(goal, today)`:
  - `left = max(target − saved, 0)`.
  - `months = (gy − y)·12 + (gm − m) + 1`, counting the current month.
  - `ratio = saved/target`.
  - `monthly = left>0 && months>0 ? left/months : 0`.
  - state: `done` if `left == 0`, `on` if `months > 0`, else `late`.
- `splitAmount(total, n)`:
  - `each = round2(total/(n+1))`.
  - `mine = round2(total − each·n)`. Your share absorbs the rounding.
- `debtPaid(d) = Σ paid[].amount`.
- `debtLeft(d) = max(round2(amount − debtPaid), 0)`. **Every open-debt total uses `debtLeft`**,
  never `amount`.
- `payDebt(d, amount, date)`:
  - `n = round2(amount)`.
  - Error if `n ≤ 0` or `n > left + 0.001`.
  - New `paid` = the old list + `{n, date}`, sorted by date.
  - `settles = n ≥ left − 0.001`.
- `debtClaim(d)`:
  - null if there's no `claimDate` or the debt is settled.
  - `amount = claimAmount>0 ? min(round2(claimAmount), left) : left`.
  - `settles = amount ≥ left − 0.001`.
- `addMonths("YYYY-MM", n)`, `todayStr`, and `getWeekStart` (Monday).

### 6.2 Dashboard month figures (`dashboard.js › renderHome`)

Let `ms` = the 1st of this month and `day` = the day of the month.

- `monthAll` = expenses with `ms ≤ date ≤ today`.
- `spent` = Σ spending in `monthAll`.
- `invested` = Σ `monthAll` − `spent`.
- `income` = Σ income in `[ms, today]`.
- **Meter**:
  - With a budget: `budgetState(spent, budget.total)`. The text reads "N% of your ₹B budget · ₹L
    left", or "₹X over your ₹B budget".
  - Otherwise, if `income > 0`: `spent/income`.
  - Otherwise the meter is hidden.
- **Pace line**: `spent/day` a day · on track for `forecast(...).total` (§6.6).
- **Today**:
  - `todaySpent` = Σ spending dated today. The count shows all of today's entries.
  - `average = day>1 ? (spent − todaySpent)/(day − 1) : 0`.
  - Meter scale = `max(todaySpent, average)·1.1`.
  - Copy: "Right on your daily average" when the gap is < 1.
- **Saved** = `income − spent`, shown as % of income. It shows "—" when there's no income.
- **You owe / Owed to you** = Σ `debtLeft` of unsettled debts per `type`, including shared debts
  from the viewer's side.
- **Where it went**: spending per category this month, sorted. Show the top 5, plus any category
  whose own limit is near or over. The bar is `share = amount/spent`, or `ratio` to the category
  limit when one is set.
- **Goals** (`renderGoals`):
  - Each goal is `withLinked(goal, linkedToGoals(portfolio(...).rows))`.
  - `need = Σ goalPace.monthly`.
  - "covered" when `income − spent ≥ need`.

### 6.3 Money score (`helpers.js › financeScore`, called with `monthShare = day/daysInMonth`)

- null when income ≤ 0. `clamp` keeps a value in 0..1.
- Parts (weight: value):
  - Saving 40: `clamp((income − spent)/income/0.2)`.
  - Investing 15: `clamp(invested/income/0.1)`.
  - Debt 20: `clamp(1 − owe/income)`, where `owe` = Σ `debtLeft` of `i-owe`.
  - Budget pace 30, only if budget > 0: `clamp((1.5 − (spent/monthShare/budget))/0.5)`.
  - Goals 10, only if there are goals: share of goals whose state isn't `late`.
- `score = round(Σ w·v / Σ w · 100)`.
- Label: ≥80 Great, ≥60 Good, ≥40 Fair, else "Needs work".
- Tip: the weakest part's tip, or "Everything is on track this month".
- Tests: `tests/score.test.mjs`.

### 6.4 Expense page figures (`dashboard.js › updateExpenseCards, renderEmis`)

- Month = Σ spending from `ms` to today.
- Year = Σ spending whose date starts with this year. This **includes future-dated** entries.
- All-time total = Σ all spending.
- Daily average = month / day of month.
- EMIs: `emiPlans` (§6.5). Header: Σ monthly of running EMIs, or "All paid off".

### 6.5 Repeating bills, EMIs and forecast inputs (`helpers.js › recurringSeries, emiOf, dueIn, dueRecurring, upcomingBills, emiPlans`)

- `dueIn(t, month)` = the template's day of month, capped at the month's last day.
- **`dueRecurring(expenses, today, skipped)`**: a series is due when all of these hold:
  - the template is `monthly`;
  - the newest entry in the series is before this month;
  - the series isn't skipped this month;
  - it isn't an EMI that's done (`paid ≥ total`);
  - `today ≥ dueIn(month)`.
  Sorted by due date, then by amount descending.
- **`upcomingBills`**: for each active series whose newest entry is before next month:
  - Due date = this month's due date.
  - It rolls to next month's due date if this month was already entered, skipped, or the date has
    passed.
  - The title is the description, or the category when the description is `-`.
- **`emiPlans`**: for each template with `emiTotal`, skipping EMIs stopped before they were paid
  off:
  - `toGo = total − paid`.
  - `from = entered this month ? next month : this month`.
  - `next = dueIn(from)`.
  - `ends = addMonths(from, toGo − 1)`.
  - `left = toGo · amount`.
  - Running EMIs come first, sorted by next date.

### 6.6 Forecast (`helpers.js › forecast`; Dashboard pace line and Insights Forecast card)

- `spent` = this month's spending up to today.
- **Usual flexible pace**:
  - Window: the last 90 days, starting from the first entry if the account is newer.
  - Fixed = `repeat=='monthly' || emiTotal>0`. Flexible = everything else.
  - When there are ≥30 flexible entries, single entries above 5% of the flexible total are dropped
    as one-offs.
  - `perDay = flexible / days`.
- `billsDue` = `dueRecurring` plus `upcomingBills` still due this month.
- `daysLeft = daysInMonth − day`.
- `total = spent + Σ billsDue + perDay·daysLeft`.
- `lastMonthSpent` = last month's spending.
- **Next month**:
  - `fixed` = Σ the amounts of active series, minus EMIs that finish this month.
  - `total = fixed + perDay·daysInNextMonth`.

### 6.7 Debts (`debt.js › updateDebtSummary`; `debt-data.js › debtsByPerson, isPersons`)

- Summary:
  - `owedToYou` = Σ `debtLeft`(they-owe, open).
  - `youOwe` = Σ `debtLeft`(i-owe, open).
  - `net = owedToYou − youOwe`.
  - People = distinct `person` among open debts.
- **By person**:
  - Key = email (`withEmail` for your own debts, `fromEmail` for shared ones), else
    `name:{lowercase name}`.
  - A name-only debt joins the person with that name when exactly one email is known for the name.
  - Per person: `owedToYou`, `youOwe`, `net` (round2), `count`.
  - Sorted by |net| descending, then by count.
- Tests: `tests/debts.test.mjs`, `tests/split.test.mjs`.

### 6.8 Investments and budget suggestion

- **`invest-data.js › portfolio(holdings, expenses, today)`**:
  - Entries = investment expenses dated ≤ today.
  - An entry belongs to a holding by `holdingId`, or failing that, by a case-insensitive name match
    on `description` (`holdingOf`). Anything else is "Not in a holding".
  - Per holding:
    - `invested` = Σ entries.
    - `valued = value is number ≥0 && valueDate set`.
    - `value = valued ? value + Σ entries after valueDate : invested`.
    - `gain = valued ? value − invested : null`.
    - `gainPct = gain/invested·100`.
    - `sip` = the newest repeat-carrying entry if it's `monthly`, with its due date taken from
      `upcomingBills`.
  - Totals:
    - `value` = Σ row values + unmatched entries.
    - `gain` = Σ over valued rows.
    - `gainPct` = gain / Σ invested of valued rows.
    - `thisMonth` and `thisYear` invested.
    - `monthly` = Σ SIP amounts.
- `linkedToGoals(rows)` = `{goalId: Σ value}`.
- `withLinked(goal) = saved + linked`.
- `afterDebts(worth, debts) = worth + owedToYou − youOwe`, using `debtLeft`.
- **Budget suggestion** (`budget-data.js › budgetFacts, suggestFromFacts, checkSuggestion`):
  - Averages over the last 3 **whole** months that have entries. Returns null with no income in
    them.
  - `keep = max(0.2·income, goalsMonthly)`.
  - `target = max(min(spent, income − keep), Σ fixed)`.
  - Flexible spending per category is scaled by `(target − fixed)/flexible`, rounded down to
    hundreds. The fixed part is rounded up to hundreds.
  - `checkSuggestion` keeps known categories only, no category below its bills, and a total that
    covers the categories and stays within income.
  - The AI only receives these numbers (`factsLines`).
  - Tests: `tests/budget.test.mjs`, `tests/invest.test.mjs`.

### 6.9 Insights (`modules/insights-data.js`)

- **`periodRange`** gives `start`, `end`, a comparison range `prevStart`/`prevEnd`, `elapsedDays`
  (days that have actually passed, at least 1), and `monthDays`.
  - `month`: from the 1st to today, compared with the same days of last month.
  - `lastMonth`: the whole previous month, compared with the month before.
  - `quarter`: the 1st of the month two months back to today, compared with the same length just
    before.
  - `year`: from Jan 1 to today, compared with the same part of last year.
  - `custom`: validated, compared with the same length just before.
- **`summarize`**:
  - `spent`, `earned`, `invested`.
  - `saved = earned − spent`; `savingsRate = (earned − spent)/earned·100`.
  - `dailyAvg = spent/elapsedDays`; `projection = dailyAvg·monthDays` (month period only).
  - `spendChange = (spent − prev)/prev·100`, null if prev = 0.
  - Categories with their share and change.
  - `movers`: the top 2 rises and top 2 falls by amount, where |diff| ≥ 1.
  - `biggest`: the top 3.
  - `weekdays`: Monday first.
  - `payments`: shares by payment method.
  - `fixed`: Σ of repeat or EMI entries.
  - Debts are a current position: open `youOwe` and `owedToYou` using `debtLeft`.
  - `hasData`.
- **`pieSlices(categories, 5)`**: with more than 5 categories, show the top 4 plus "Other".
- **`budgetReport`**: for each month the period touches:
  - `limit = total · share`, where `share` is the fraction of the month's days inside the period.
    The current month counts whole.
  - The current month is judged on `max(forecast.total, spent)`.
  - A category's projection in the current month = its fixed part + flexible/elapsed·days.
  - `within`/`judged` counts. Per category: `overIn`, `avg`, `worst`.
- **Ask** (`ask-data.js › guessSpec, readSpec, runQuery, answerFor`):
  - The question becomes a spec `{type, text, tag, categories, from, to, measure}`, either from the
    AI (which gets only the question, today's date and the category names) or on-device
    (`guessSpec`).
  - `runQuery` filters local entries:
    - Investments are left out unless asked for.
    - Every word must match a field.
    - Income is included only when there's no category or tag.
  - Result: `total`, `count`, `biggest`.
  - Tests: `tests/ask.test.mjs`, `tests/insights.test.mjs`.

### 6.10 History (`modules/history-data.js`)

- `historyRange`:
  - `month` = [the 1st, today].
  - `lastMonth` = [the 1st of last month, its last day].
  - `quarter` = [the 1st of the month two months back, today].
  - `year` = [Jan 1, today].
  - `custom`.
  - Anything else = open-ended.
- `filterHistory`, `totalsBy`, `tagsInUse` and `historyDay` are described in §2.6.
- Tests: `tests/history.test.mjs`, `tests/dates.test.mjs`.

### 6.11 Income page (`income.js › updateIncomeSummaryCards`)

- Month = Σ from the 1st to today.
- Year = Σ from Jan 1 to today.
- Recent = the newest 8.
- Source chips = the 6 most frequent sources. Bank suggestions are ranked by frequency.

---

## 7. Everything else

### 7.1 `worker/` (Cloudflare Worker `spendwise-ai`)

`worker/src/index.js` and `worker/src/push.js`. Base URL `https://spendwise-ai.spendwise-ai.workers.dev`
(`assets/js/config/ai.js › AI_PROXY_URL`).

- **Requests**: every request is `POST` with `Authorization: Bearer <Firebase ID token>`.
  - The token is verified against Google's JWKS for project `spendwise-app-f7227`
    (`index.js › fetch`).
  - Unverified emails get 403.
  - CORS restricts browsers only; a native client isn't affected.
- **`POST /`, AI proxy** (`index.js`):
  - Body: `{model, messages, max_tokens, temperature}`.
  - Allowed models:
    - `@cf/qwen/qwen2.5-coder-32b-instruct`: voice and CSV.
    - `@cf/meta/llama-3.3-70b-instruct-fp8-fast`: coach, Ask and budget.
    - `@cf/meta/llama-4-scout-17b-16e-instruct`: receipts.
  - Limit: 100 requests per user per UTC day; over the limit gets **429**.
  - Response: `{choices:[{message:{content}}]}`.
  - **Privacy rule**: no descriptions, notes or names go to the AI. Only number facts or the
    question go (web `CLAUDE.md`).
- **`/push/*` routes** (`push.js › handlePushRoute`):

  | Route | Body | Effect |
  |---|---|---|
  | `/push/subscribe` | `{endpoint, remindAt, tz, keys?}` | Registers a device |
  | `/push/unsubscribe` | `{endpoint}` | Removes a device |
  | `/push/logged` | `{date}` | Skips today's reminder on every device of the user |
  | `/push/bills` | `{bills, symbol}` | Bill-due alerts |
  | `/push/months` | `{totals, symbol}` | Month-end summary |
  | `/push/debt` | `{debtId, symbol}` | Pushes the `withEmail` person about a new shared debt. Accepted only if the debt is less than 1 h old. |
  | `/push/claim` | `{debtId, symbol}` | Pushes the debt's creator about a claim |

  - A cron every 15 min sends the daily reminders, bill-due alerts and month summaries.
  - `/push/bills` and `/push/months` only store data when the user has a registered device.
- **Android impact**:
  - **Endpoint restriction**: `validEndpoint` accepts only browser Web Push hosts (FCM web,
    Mozilla, Apple, Windows). A native Android FCM token **can't register**. Native push needs a
    Worker change in the web repo (Q3).
  - **Calls Android should still make** for parity, even without push:
    - `/push/debt` after creating a shared debt.
    - `/push/claim` after a claim.
    - `/push/logged` after adding any entry.
    These keep the other person's web devices and the user's own web reminders correct. They need
    an HTTP client, which isn't in the approved stack (Q4).

### 7.2 Service worker (`sw.js`) — **skip on Android**

- Precaches the static files and serves HTML stale-while-revalidate, other assets cache-first.
  Writes are never cached.
- **Push display**:
  - A message push (`{title, body, tag, url}`) shows as is.
  - An empty push is the daily reminder: "Log today's spending" / "Add what you spent today. It
    only takes a few seconds." (tag `daily-reminder`), which opens Add Expense.
- **Notification tap**: focuses or opens the app at `data.url`.
- **What to replicate**: only the reminder copy and tap target, if push is built (Q3). Offline app
  loading is native on Android.

### 7.3 Offline writes (`modules/offline-queue.js`, `network-status.js`)

- **The web's queue**:
  - Lives in `localStorage['spendwise_offline_queue']`.
  - Each item: `{id, type: expense|income|debt, data, timestamp, retries}`.
  - On reconnect or page load it runs `setDoc(doc(coll, item.id), {...data, createdAt: serverTimestamp()})`.
    The queue id becomes the doc id, so a retry can't create a duplicate.
  - Items belonging to another uid are skipped.
  - Failed items stay queued with `retries+1`.
  - Toasts: "N offline transactions synced!" / "N could not sync. Kept and will retry."
  - Pending items show "waiting to sync" and can't be edited or deleted. Holdings can't be saved
    offline.
- **Android**: Firestore's offline persistence does all of this natively. Writes are queued and
  `serverTimestamp` resolves on commit; use `metadata.hasPendingWrites` for the pending badge.
  **Decided: don't port the queue** (Q10).

### 7.4 Entry helpers (later, not in v1; §8.1)

- **Voice entry** (`voice-command.js`):
  - Web Speech, then the AI (Qwen) turns speech into one or more entries of any type.
  - A review card follows (`voice-data.js › cleanEntry`), then a batch write with pre-assigned ids.
- **Typed text** (same module).
- **Receipt photo** (Llama-4 Scout).
- **SMS paste** (`sms-parse.js › parseSms`): on-device bank/UPI SMS parser, no AI. Tests:
  `tests/sms.test.mjs`.
- **Share target** (`manifest.json`): text shared to the app opens `expense.html`.
- **CSV import from a bank statement** (`csv-upload.js`):
  - The AI extracts transactions, the user previews them, then they're batch-written in chunks of
    500.
  - Income gets **no `encoding`**; expenses get **no `tags`**.

### 7.5 Daily reminder (`reminders.js`, Settings › Notifications)

- The switch and time are synced through `settings.remindOn` and `settings.remindAt` (default
  21:00).
- Each device keeps its own push subscription with the Worker; the device time zone is sent.
- `reminderSync` (`helpers.js`) decides what a device does when the account switch differs from the
  device state. Tests: `tests/reminders.test.mjs`.
- **Decided (Q3):** Android schedules a **local** daily notification through WorkManager, honouring
  `remindOn`/`remindAt`. It doesn't register with the Worker. Toggling the switch writes
  `remindOn`/`remindAt` with `mergeFields`, the same as the web. On sign-in, and whenever
  `settings` changes, the device's scheduled work is updated to match.

### 7.6 Notification bell (`notifications.js`; `helpers.js › notificationsFrom, pushTagsFor, mergeIds`)

- **Items**:
  - For each debt shared with you: `{id: debtId+':split'}`, plus `debtId+':settled'` once it's
    settled.
  - For each of your own debts with a claim: `debtId+':claim:'+claimDate+':'+amount`.
- Items are sorted newest first, with an unread badge.
- `settings.notif.seen` and `cleared` are each capped at the last 200.
- If Android builds the bell, it **must use the same id format** so clearing an item syncs both
  ways.

### 7.7 App lock (`modules/app-lock.js`) — device only

- A WebAuthn platform credential, stored locally.
- The app locks on start, and again after 5 min in the background.
- Nothing about it is in Firestore.
- Android equivalent: BiometricPrompt. That needs `androidx.biometric`, a library not in the stack
  (Q2/Q4).

### 7.8 Backup and restore (`backup.js`)

- **Export**: JSON `{version:"1.0", exportDate, expenses[], income[], debts[], holdings[]}`. Each
  record is `{id, ...fields}`; Timestamps are serialized by `JSON.stringify` as
  `{seconds, nanoseconds}`.
- **Import**:
  - Keeps only records whose `uid` matches the current user.
  - Validates each one (`validateRecord`, `holdingRecord`).
  - Batch-`set` in chunks of 500, keeping each id when it matches `/^[A-Za-z0-9_-]{1,128}$/`.
  - Resets `createdAt`.
- **Compatibility**: a file made on Android should restore on the web, and the reverse. If ported,
  keep the exact format. Tests: `tests/backup.test.mjs`.

### 7.9 Can be skipped safely (web-only)

- `sw.js` caching.
- Settings › Refresh the app.
- `pickers.js`: custom select and date sheets.
- `accessibility.js`: use Compose semantics instead.
- `event-delegation.js`.
- Hash routing.
- The desktop layout and pagination.
- The sessionStorage data cache.
- The legacy `spendwise_budget` localStorage migration (`budget.js › onSettings`).

---

## 8. Decisions (answers to the open questions, 2026-10-26)

| # | Topic | Decision |
|---|---|---|
| Q1 | Encryption | Plain values only, written with `encoding: "plain"`. No crypto class and no `data/crypto/`. **Resolved:** no XOR-encoded docs exist in production (§3.9), so no decoder is needed. |
| Q2 | v1 scope | See §8.1. |
| Q3 | Notifications | A local daily reminder through WorkManager, driven by `settings.remindOn` and `settings.remindAt` (default 21:00). No FCM and no Worker push. Bill alerts, debt pushes and month summaries are not in v1. |
| Q4 | Libraries | No HTTP client in v1, so the Worker is never called. Charts are drawn with Compose Canvas. Biometric comes later, with the app lock. |
| Q5 | Per-device prefs | Currency, theme, skipped bills and last payment method stay per device, in DataStore. Nothing new goes into `settings/{uid}`. |
| Q6 | Hosting | The web is on GitHub Pages, with the Cloudflare Worker `spendwise-ai`. `CLAUDE.md` is corrected. |
| Q7 | Amount type | Mirror the JS SDK: a Long for whole numbers and a Double otherwise. |
| Q8 | Design | Follow `CLAUDE.md`: one typeface, no glass and no shadows. Keep the colour meanings: green for money in, red for owed or over, one accent for money totals and the selected state. |
| Q9 | Web quirks | Port them exactly, so Android and web totals always agree. They're listed in §9, so both clients can be fixed together later. |
| Q10 | Offline | Firestore offline persistence; the web's queue is not ported (§7.3). Use `metadata.hasPendingWrites` for "waiting to sync". |
| Q11 | Unverified email users | Sign them out with the web's message, and add a **Resend verification email** action (`sendEmailVerification` on the just-signed-in user, then sign out). |
| Q12 | Password rules | Keep the web's rules: at least 8 characters, with a lowercase letter, an uppercase letter, a digit and a special character (`dashboard.js › handleSignup`). The error message lists all five rules accurately, unlike the web's `friendlyErr` copy (§9). |
| Q13 | Form factor | Phone only for v1. |
| Q14 | Real documents | **Received 2026-10-26.** §3 was checked against them; the results are in §3.9. The data layer is unblocked. |
| Q15 | Account deletion | In scope, see §8.2. |
| Q16 | Shared-debt push | Acceptable for v1. The other person gets no push for debts created on Android, but sees the debt the next time they open the app. |

### 8.1 v1 scope

**In v1**: entry and read screens for every collection, so no web data is invisible on Android.

- Auth: email and password, Google, forgot password, resend verification.
- Dashboard, with the tabbed time views (§2.10), due bills, score and goals.
- Expenses: add, edit and delete, repeating bills and EMIs, and split (§2.3, §2.4).
- Income (§2.5).
- Debts, with sharing, claims and part-payments (§2.7).
- History, with filters and CSV export (§2.6).
- Insights without AI: forecast and period cards (§6.6, §6.9).
- Investments (§2.9).
- Budgets and goals (§2.8), without the AI budget suggestion.
- Settings: appearance, currency, budget, reminder, account deletion, log out.

**Later**:

- AI: coach, Ask, budget suggestion.
- Voice, receipt and SMS entry, and the share target.
- CSV import.
- Backup and restore.
- App lock (biometric).
- Notification bell.
- Server push: FCM and Worker routes.

### 8.2 Account deletion (Q15)

1. Settings › Delete account opens a confirmation dialog. It says what will be deleted, and that
   debts shared with other people disappear for them too. The user types or taps to confirm.
2. Delete only documents the user owns:
   - `whereEqualTo("uid", uid)` on `expenses`, `income`, `debts` and `holdings`;
   - plus `settings/{uid}`.

   Delete in batches of ≤500. The rules allow owner deletes (§4.1). Debts shared *with* the user
   belong to someone else and are left alone.
3. Then `FirebaseUser.delete()`. If it fails with `FirebaseAuthRecentLoginRequiredException`,
   reauthenticate (password prompt, or Google through Credential Manager), then retry.
4. **Order matters.** Reauthenticate *before* deleting data, so a failed reauth can't leave an
   account with no data. Then delete the data, then the auth account.
5. Clear DataStore, cancel the reminder work, and go to sign-in.

---

## 9. Known web quirks (ported exactly; fix later in both clients)

Android reproduces each of these, so the two clients always agree. Each Android port carries a
`// web quirk: §9.N` comment, so all of them can be found and fixed together.

1. **The Daily and Weekly charts ignore the month and year picker.** They always show the last 7
   days, or the last 4 weeks (§2.10, `dashboard.js › getTrendData`).
2. **The tabbed-views table includes investments, while the summary and chart exclude them**
   (§2.10, `dashboard.js › renderDashboardTable`).
3. **The year total includes future-dated entries** (§6.4, `dashboard.js › updateExpenseCards`).
4. **Skip month is device-local.** A bill skipped on one device is still due on another (§2.3,
   `localStorage['spendwise_skip:…']`). Android keeps it in DataStore, so it doesn't sync either.
5. **Editing an expense doesn't change `emiTotal`, `emiPaidBefore` or `holdingId`.** Those fields
   are kept as they were (§2.3).
6. **CSV import writes income without `encoding`, and expenses without `tags`** (§7.4,
   `csv-upload.js`). Android doesn't import in v1, but its readers must accept those docs.
7. **Unverified sign-in is handled inconsistently.** `index.html` signs out unverified users of any
   provider; the other pages sign out only `password` users (§2.1). On Android it makes no
   difference, because Google accounts arrive verified. Android checks once, after sign-in, for
   password users.
8. **The `friendlyErr` weak-password copy says only "at least 8 characters"**, but sign-up also
   requires lowercase, uppercase, a digit and a special character (§2.1). **This is the one quirk
   Android does not copy (Q12).** Android's message lists all five rules. The rules themselves are
   identical.
9. **Reopening a paid-off debt removes its last payment** (§2.7,
   `debt.js › confirmDebtReactivate`).
10. **`amount` is read with JS `parseFloat`**, so a leading-number prefix such as `"12abc"` reads
    as 12 (§3.7). Android's reader must match: parse the longest leading decimal prefix, otherwise 0.
11. **Legacy XOR-encoded expenses would show as 0 on the web**, because `parseFloat` of the base64
    gives NaN (§5). None exist in production (§3.9). Android's reader gives the same 0 if one ever
    appears.
12. **A claim is checked against the debt's original `amount`, not what's left**, both in the rules
    and in the client (§3.3, §4.1). On payment, `debtClaim` clamps it to what's left (§6.1).

---

## Appendix: the original open questions

These are kept for context. The answers are in §8.



**Q1. Encryption.** Nothing is encrypted, but `CLAUDE.md` asks for a `data/crypto/` class that
matches the web's format. Choose one:

- (a) Plain values only, like the web today, and no crypto class. Optionally, update `CLAUDE.md`.
- (b) (a) plus a read-only `LegacyXorDecoder` in `data/crypto/` that decodes `amount`,
  `description` and `notes` on expenses that match the §5 detector. This makes Android show those
  old entries correctly, while the web shows them as 0 or base64.
- (c) A one-off migration that rewrites the legacy docs as plain text. That needs your approval,
  because it changes shared data.

First, check whether any such documents still exist: expenses without `encoding` and with a
non-numeric string `amount`.

**Q2. Feature scope for v1.** The `CLAUDE.md` layout covers auth, expenses, dashboard and settings.
The web also has the features below. Which of them are in v1, and which later?

- Income, Debts (sharing, claims, part-payments), History and CSV export, Insights.
- Investments, Budgets, Goals, repeating bills and EMIs, Split.
- Voice, receipt, SMS, CSV import, AI coach, Ask, budget suggestion.
- Backup and restore, app lock, notification bell.

My suggestion: v1 is the data-entry and read screens for every collection, so no web data is
invisible on Android. AI, voice and import features come later.

**Q3. Push notifications.** Choose one:

- (a) None on Android for now.
- (b) Local scheduled reminder (WorkManager), driven by `settings.remindOn`/`remindAt`. This
  doesn't cover bill alerts, debt pushes or month summaries.
- (c) A native FCM path. That needs Worker and D1 changes in the web repo, which this project must
  not touch, so the web owner would make them.

**Q4. HTTP client.** Calling the Worker needs an HTTP client: for `/push/debt`, `/push/claim` and
`/push/logged` (parity), and for AI. The approved stack has none. Can I add OkHttp (or Ktor)?
`androidx.biometric` (app lock) and a chart library would need the same approval. Otherwise I'll
draw charts with Compose Canvas.

**Q5. Currency, theme and skipped bills are per device on the web.** Choose one:

- Keep them per device on Android, in DataStore.
- Sync them through `settings/{uid}`. That needs a rules change, because `validSettings` rejects
  new keys.

**Q6. Hosting note.** `CLAUDE.md` says Netlify, but the web app is on GitHub Pages. Should I
correct `CLAUDE.md`?

**Q7. Amount type on write.** Choose one:

- Mirror the JS SDK: a Long for whole numbers and a Double otherwise. This is my recommendation,
  because it's identical to web-written docs.
- Always a Double. The web reads both.

**Q8. Design differences.** The web uses two typefaces (Plus Jakarta Sans and Fraunces), gold and
indigo accents, soft shadows and a "glass" tab bar. Android's `CLAUDE.md` asks for one typeface, no
decorative shadows and no glass. I'll follow `CLAUDE.md`. Should I keep the web's colour meanings:
gold for money and selected, green for money in, red for owed or over?

**Q9. Dashboard "tabbed time views".** The web's Daily/Weekly/Monthly/Yearly tabs have quirks:

- The Daily and Weekly charts ignore the month and year picker.
- The table includes investments while the summary excludes them.
- Year totals include future-dated entries.

Should I port them exactly, or fix them?

**Q10. Offline.** Can I use Firestore's offline persistence instead of porting the localStorage
queue (§7.3)? One side effect: the doc ids of offline writes will be auto-ids, not queue ids, which
is harmless.

**Q11. Unverified email and password users.** The web signs them out at login. Android will do the
same, with the same message, and offer "Resend verification email". The web doesn't have that
button. Should I add it?

**Q12. Sign-up password policy.** The web requires upper, lower, digit and special characters
(`handleSignup`), but the `friendlyErr` copy says only "at least 8 characters". Should I keep the
web's rules?

**Q13. Phone only?** Is Android phone-only, or should I also lay out for tablets and foldables?
The web's desktop two-column layout could be the model.

**Q14. Example documents.** §3's examples are built from the write code. Do you want to paste one
real document of each type from the Firebase console, so I can confirm field types? This matters
most for `amount` int vs double, and for any legacy fields.

**Q15. Account deletion.** The web has no account or data deletion. Google Play requires an
in-app account deletion path for apps that create accounts. Should it be in scope, and should it
delete only the user's own documents?

**Q16. Shared-debt parity edge.** `/push/debt` only announces debts less than 1 h old. If Android
can't call it (no HTTP client), the other person gets no push for debts created on Android. They
still see the debt the next time they open the app. Is that acceptable for v1?
