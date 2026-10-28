# SpendWise Android: plan to reach web parity

This plan covers every feature the web app has that Android lacks as of slice 11 (commit 0629b24).
Each slice is built, tested and committed on its own (CLAUDE.md workflow rules). The slices are
ordered so that the work needing no new library or decision comes first. Later slices are blocked
on one of the decisions in §0.

Every slice follows the same "done" bar:

- `./gradlew assembleDebug testDebugUnitTest` passes with no new warnings.
- The slice is installed on the emulator and checked in light and dark themes.
- Loading, empty and error states are handled.
- Any data written round-trips with the web app.
- It is one commit, named "Slice N: …".

Web logic is ported exactly, quirks included, with `// web quirk: §9.N` comments.

---

## 0. Decisions needed before the blocked phases

| # | Decision | Needed by | Options |
|---|---|---|---|
| D1 | **HTTP client.** The Worker (AI and `/push/*`) needs HTTP, and CLAUDE.md says "No HTTP client in v1". | Phase C | **Decided: (a).** (a) `java.net.HttpURLConnection` plus the kotlinx-serialization already installed, so no new library (recommended). (b) Add OkHttp. |
| D2 | **Biometric library.** App lock needs `androidx.biometric`, which isn't in the stack. | Slice 18 | **Decided: not now (slice 18 parked).** Approve `androidx.biometric:biometric`, or drop app lock. |
| D3 | **Profile photo.** Showing the Google photo needs image loading. | Slice 14 | (a) Keep the initial only (recommended for now). (b) Add Coil. (c) Decode the photo URL by hand with HttpURLConnection, once D1 is decided. |
| D4 | **Native push.** The Worker's `validEndpoint` only accepts browser Web Push hosts, so a native FCM token can't register (spec §7.1). This needs a Worker change **in the web repo**, which this project must not modify. | Phase D | Change the Worker in the web repo first, then add `firebase-messaging`. Or keep the local WorkManager reminder only. The Worker change is specified in `docs/WORKER_FCM_SPEC.md`. |
| D5 | **AI privacy consent on Android.** The web shows a consent dialog once (`ensureConsent`). | Phase C | **Decided: as recommended.** Show the same text once per device, stored in DataStore (recommended). |

---

## Phase A: in-scope parity gaps (no new libraries)

### Slice 12: Deep links, app shortcuts, reminder tap
Spec: §1.0 deep links, §7.5.

- **Navigation entry points.** Handle the `Intent` in `MainActivity`, pass it to `AppShell` as a
  start target, and keep the auth gate: a signed-out user goes to sign-in, then on to the target.
  - `spendwise://add`, `…/history`, `…/insights`, `…/dashboard`. These match the web's
    `index.html#history|add|insights|dashboard`.
  - `…/history?tab=&q=&from=&to=&cat=` prefills the History filters. Reuse `HistoryViewModel`'s
    filter state, with no new filter code.
  - `…/history#income` (`tab=income`).
- **Static app shortcuts** (`res/xml/shortcuts.xml`): "Add expense" and "Add income". This is the
  web's `?source=shortcut`.
- **Reminder notification**: change its `PendingIntent` so it opens Add Expense, not the
  Dashboard.
- **Code**:
  - `ui/navigation/DeepLink.kt`: a pure parser from `Uri` parts to a route plus `HistoryFilter`.
  - Wire it into `MainActivity` (`onCreate` and `onNewIntent`) and `ReminderWorker`.
- **Tests**: unit-test the parser on every form above, plus junk input (unknown tab, bad dates
  falling back to no filter).
- **Verify**: run `adb shell am start -d "spendwise://history?tab=income&q=rent"` both cold and
  warm. Tap the reminder.

### Slice 13: Add Expense parity (tile order, More details, split suggestions)
Spec: §1.3, §1.5 (`people.js`).

- **Category tile order** (`orderCategoryTiles`): sort tiles by use in the last 60 days, once
  per screen visit, not live while typing.
  - Port this as `domain/Expenses.kt › orderCategories(expenses, today)`.
  - Ties keep the default order.
  - Edit mode keeps the default order if the web does too; check against `dashboard.js`.
- **More details**: a collapsed section holding Payment method, Card name, Notes, Repeats every
  month (with EMI fields) and Tags. The collapsed summary shows the payment method, as on the web.
  The open/closed state isn't persisted unless the web persists it.
- **Split name suggestions**: reuse `peopleFrom(debts)` and the suggestion dropdown from
  `DebtsScreen`.
  - Move the suggestion composable to a shared `ui/components` file instead of copying it.
  - A known email fills in the name (people.js).
- **Tests**: `orderCategories` covering the 60-day window boundary, ties, and no history.
- **Verify**: add expenses in "Travel" on the web, then open Add on Android. Travel should come
  first.

### Slice 14: Signed-in header (avatar menu, pending badge, persistent errors)
Spec: §1.0 header and toasts, §7.3.

- **Header**: a top bar on every signed-in tab with an avatar menu (name, email, Log out). It uses
  the initial or the photo, per D3. The bell slot stays empty until slice 17.
- **Pending-sync badge**: the count of documents with `metadata.hasPendingWrites` across
  expenses, income and debts, plus an offline indicator.
  - The repositories already read `hasPendingWrites`. Expose a combined
    `Flow<Int>` from one place: a new `data/SyncStatus.kt` combining the existing repository flows.
  - Connectivity comes from `ConnectivityManager.NetworkCallback` (platform).
  - Wording copied from `offline-queue.js › updatePendingBadge` and `network-status.js`.
- **Error messages stay until dismissed**: errors use `SnackbarDuration.Indefinite` with a
  dismiss action, and success messages keep the short duration. This is a one-place change if
  every screen uses a shared snackbar helper. Otherwise add one helper and route errors through
  it.
- **Tests**: unit-test the pending-count combine with fake flows.
- **Verify**: turn on airplane mode, add 2 entries, and check that the badge shows 2. Turn
  airplane mode off: the badge clears and the web shows both entries.

---

## Phase B: later features that need only Firebase

### Slice 15: Backup and restore
Spec: §7.8. Web tests: `tests/backup.test.mjs`.

- **Export**: JSON `{version:"1.0", exportDate, expenses[], income[], debts[], holdings[]}`.
  - Each record is `{id, ...fields}`.
  - Timestamps are written as `{seconds, nanoseconds}`, exactly as `JSON.stringify` writes them.
  - Saved through the existing `data/export/ExportFiles` share flow.
- **Import**: pick a file with `ActivityResultContracts.OpenDocument`.
  - Keep only records whose `uid` is the current user.
  - Validate with ports of `validateRecord` and `holdingRecord`.
  - Batch-`set` in chunks of 500, keeping the id when it matches `^[A-Za-z0-9_-]{1,128}$`.
  - Reset `createdAt` to `serverTimestamp()`.
  - Show a preview count ("N expenses, N income…") before writing.
  - **Risk**: this is the one place Android does a whole-document `set`. It is allowed only
    because the web's restore does the same. Note it in a code comment.
- **Code**: `domain/Backup.kt` (pure validate and serialize), `data/BackupRepository.kt`, and
  Settings › Your data › Backup & restore.
- **Tests**: round-trip with the web's own test vectors, copied from `tests/backup.test.mjs`
  (foreign-uid rejection, bad ids, holding strictness).
- **Verify**: export on Android and restore on web on a **throwaway account**, and the reverse.
  Totals must match.

### Slice 16: Ask about your spending (on-device)
Spec: §1.7 item 1, §6.9 Ask. Web tests: `tests/ask.test.mjs`.

- Port `ask-data.js › guessSpec, readSpec, runQuery, answerFor` to `domain/Ask.kt`. The
  question becomes `{type, text, tag, categories, from, to, measure}` and runs on local entries.
- UI: the question box with example chips at the top of Insights. The answer links to History
  with the filters set. That link reuses the slice 12 deep-link route, which is why this slice
  comes after it.
- Only the on-device `guessSpec` path is built here. The AI path is added in slice 22.
- **Tests**: port every case from `tests/ask.test.mjs`.

### Slice 17: Notification bell
Spec: §7.6, §3.5 `notif`.

- Port `helpers.js › notificationsFrom, mergeIds` to `domain/Notifications.kt`. Item ids must be
  the same format: `debtId:split`, `debtId:settled`, `debtId:claim:date:amount`.
- `settings.notif.{seen,cleared}` are each capped at 200, newest last. Write them with
  `mergeFields("notif")` only, since `settings` is a strict schema.
- A bell in the slice 14 header shows the unread badge and a sheet listing the items. Tapping an
  item opens Debts. There are Mark read and Clear actions.
- **Tests**: id format, sort order, the 200 cap, and merging with ids the web already wrote.
- **Verify**: clear an item on Android and check it's gone on the web, and the reverse.

### Slice 18: App lock (blocked on D2)
Spec: §7.7.

- `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
- Lock on start and after 5 minutes in the background, using `ProcessLifecycleOwner` (already
  available through lifecycle).
- The setting is device-only (DataStore); nothing goes to Firestore.
- Settings › Security switch. Hide it when `BiometricManager.canAuthenticate` is false.
- **Tests**: the pure "should lock" timing function.

### Slice 19: SMS paste and share target
Spec: §7.4. Web tests: `tests/sms.test.mjs`.

- Port `sms-parse.js › parseSms` to `domain/Sms.kt`. It runs on-device with no AI.
- Add an `ACTION_SEND text/plain` intent filter. Shared text opens Add Expense prefilled with the
  `parseSms` result, or with the raw text in Description when parsing fails.
- **No `READ_SMS` permission.** It's a paste or share flow only, as on the web, because Play
  restricts SMS permissions.
- **Tests**: port every case from `tests/sms.test.mjs`.

---

## Phase C: Worker features (blocked on D1, and D5 for AI)

### Slice 20: Worker client and the parity push calls
Spec: §7.1.

- `data/worker/WorkerClient.kt`:
  - `POST` with `Authorization: Bearer <Firebase ID token>`.
  - Maps 429 to "Daily AI limit reached" and 403 to "verify your email".
  - Uses a timeout. The privacy rule (no descriptions, notes or names in AI calls) is enforced in
    the callers.
- Make the calls the web relies on, fire-and-forget, with failures logged and never shown as
  errors:
  - `/push/logged {date}` after adding any entry.
  - `/push/debt {debtId, symbol}` after creating a shared debt.
  - `/push/claim {debtId, symbol}` after a claim.
- **Tests**: request building and the status-to-message mapping (pure).
- **Verify**: add a shared debt on Android, and the other account's web device gets the push.

### Slice 21: AI budget suggestion
Spec: §6.8 budget suggestion. Web tests: `tests/budget.test.mjs`.

- Port `budget-data.js › budgetFacts, suggestFromFacts, checkSuggestion` (pure) and `factsLines`.
  Only these numbers go to the AI, using the Llama 3.3 model.
- Add a "Suggest" action in the budget dialog. The AI result goes through `checkSuggestion`, and
  `suggestFromFacts` is the fallback.
- **Tests**: port `tests/budget.test.mjs`.

### Slice 22: AI coach and AI Ask
Spec: §1.7 item 5.

- **Coach**:
  - Topic chips Overview, Spending, Savings, Trends, Debt, then a chat.
  - The chat is saved per user for 24 hours (DataStore, device-only like the web's
    `saveChat`).
  - The consent dialog shows once (D5).
  - Only number facts are sent. Port the web's fact builders exactly.
- **AI Ask**: the AI receives only the question, today's date and the category names, and
  returns a spec. Fall back to the slice 16 `guessSpec` on any error.
- **Tests**: the fact builders contain no description, note or name fields (a privacy test).

### Slice 23: Voice and typed entry
Spec: §7.4 voice.

- Use the platform `SpeechRecognizer` (`RECORD_AUDIO` requested on use), plus a typed-text box.
  The text goes to the Qwen model and comes back as entries of any type.
- Port `voice-data.js › cleanEntry` and show a review card. The batch write uses pre-assigned ids
  through the existing repositories' writers, so the document shapes stay identical.
- Holding the Add tab opens voice entry, as on the web.
- **Tests**: port the `cleanEntry` cases.

### Slice 24: Receipt scan
Spec: §7.4 receipt.

- Take a photo with `ActivityResultContracts.TakePicture` into the app cache (`FileProvider`
  already exists). Downscale and send it as base64 to Llama-4 Scout.
- The result prefills Add Expense for review. Nothing is saved without the user confirming.
- Delete the photo from the cache afterwards.

### Slice 25: CSV import from a bank statement
Spec: §7.4 CSV import.

- Pick a file. The AI (Qwen) extracts transactions, the user previews and unticks rows, then the
  rows are batch-written in chunks of 500.
- **Port the web's shape quirks exactly**: imported income gets **no `encoding`**, and imported
  expenses get **no `tags`**.
- **Tests**: the row-to-document mappers match the web's shapes field for field.

---

## Phase D: native push (blocked on D4, needs a web-repo change first)

### Slice 26: FCM push
- Contract with the Worker: `docs/WORKER_FCM_SPEC.md` (§6 is this slice's checklist).
- A prerequisite done outside this project: the Worker accepts native FCM tokens. That means a
  `validEndpoint` change, a token-based subscribe body, and sending through the FCM HTTP v1 API.
- Android: add `firebase-messaging` from the BoM, plus a `FirebaseMessagingService` that
  registers the token through `/push/subscribe {endpoint, remindAt, tz}`.
- Handle bill alerts, debt and claim pushes, and month summaries.
- Decide whether the local WorkManager reminder stays as the fallback or is replaced, so users
  never get two reminders. **Decided:** the Worker sends the reminder once it accepts this device,
  and the local job is cancelled then. If the Worker refuses (409, more than 10 devices) or can't
  be reached, the local job runs, and ReminderSync tries the Worker again on the next settings change.

---

## Not planned (web-only, spec §7.9)

- The desktop sidebar (Android is phone-only in v1).
- "Refresh the app" (a service-worker concern).

## Order at a glance

| Slice | Feature | Blocked by |
|---|---|---|
| 12 | Deep links, shortcuts, reminder tap | — |
| 13 | Tile order, More details, split suggestions | — |
| 14 | Header, pending badge, persistent errors | D3 (photo only) |
| 15 | Backup & restore | — |
| 16 | Ask (on-device) | slice 12 |
| 17 | Notification bell | slice 14 |
| 18 | App lock | D2 |
| 19 | SMS paste, share target | — |
| 20 | Worker client, push calls | D1 |
| 21 | AI budget suggestion | D1, D5 |
| 22 | AI coach, AI Ask | D1, D5, slice 16 |
| 23 | Voice and typed entry | D1, D5 |
| 24 | Receipt scan | D1, D5 |
| 25 | CSV import | D1, D5 |
| 26 | FCM push | D4 (web repo) |
