# SpendWise Android — Project Guide for Claude Code

Native Android rewrite of the SpendWise web app (vanilla JS + Firebase, hosted on GitHub Pages, with a Cloudflare Worker for AI and push). The web app stays live and shares the same Firebase project, so both clients must read and write the same Firestore data without breaking each other. v1 talks only to Firebase, never to the Worker.

## Source of truth

- `ANDROID_SPEC.md` describes every screen, flow, and Firestore schema. Follow it. If code and spec disagree, stop and ask; don't silently diverge.
- The web app repo is reference only. Never modify it from this project.

## Stack
- Kotlin, Jetpack Compose, Material 3
- MVVM: Composable screens → ViewModel (StateFlow UI state) → Repository → Firebase
- Kotlin Coroutines + Flow (no RxJava, no LiveData)
- Firebase via the latest stable Firebase BoM: Auth, Firestore
- Google Sign-In through Credential Manager (not the deprecated GoogleSignInClient)
- Navigation Compose with type-safe routes
- DataStore for per-device preferences (theme, currency, last payment method, skipped bills, last-selected tab); these are never synced
- Firestore offline persistence for offline writes (no custom queue)
- WorkManager for the local daily reminder
- Charts drawn with Compose Canvas (no chart library)
- No HTTP client library: Worker calls (Phase C) use `java.net.HttpURLConnection` with kotlinx-serialization (decision D1)
- Hilt for dependency injection
- minSdk 26, targetSdk/compileSdk = latest stable
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)

## Package / application ID

- `com.spendwise.app` (existing ID, so keep it exactly; never change applicationId)
- versionCode must be higher than any previously published build under this ID

## Package layout

```
app/src/main/java/com/spendwise/app/
  data/          repositories, Firestore models, mappers, DataStore prefs
  domain/        plain Kotlin models + business logic (no Android imports), ported from the web's helpers.js and *-data.js
  reminder/      WorkManager daily reminder
  ui/
    theme/       colors, typography, shapes
    navigation/  tab bar, More sheet, routes
    auth/        login, signup, forgot password, verify email
    dashboard/   month at a glance, due bills, score, goals, tabbed time views
    expenses/    add/edit, split, EMIs
    income/      add/edit, recent
    debts/       add, payments, claims, by person, settled
    history/     search, filters, CSV export
    insights/    forecast, period cards (no AI)
    invest/      holdings, money put in
    budget/      budget dialog (no AI suggestion)
    goals/       goal dialog
    settings/    appearance, currency, budget, reminder, account deletion, log out
  di/            Hilt modules
```

## v1 scope

- In v1: entry and read screens for every collection. That covers expenses, income, debts (with sharing, claims and part-payments), history with CSV export, non-AI insights, investments, budgets, goals, repeating bills and EMIs, split, the local reminder, and account deletion.
- Later: AI (coach, Ask, budget suggestion), voice/receipt/SMS entry, CSV import, backup and restore, app lock (biometric), notification bell, server push.
- Phone only for v1.

## Design rules (strict)
- Minimalist and clean: high contrast, generous whitespace, restrained typography
- No gradients, glows, shadows-as-decoration, or ornamental illustrations
- Light and dark themes, both high contrast; follow the system setting by default with a manual override
- Use Material 3 components, restyled to match the above; no dynamic color
- Stick to 1 typeface and 2 or 3 weights
- Colour meanings: green for money in, red for owed or over budget, one accent for money totals and the selected state. Colour never carries meaning alone; the words beside it say the same
- No glass effects, no shadows

## Data compatibility (critical)

- Firestore collection names, field names, and types must match the web app exactly
- Dates and amounts must be stored in the same format the web app uses (see spec)
- No field is encrypted: read and write plain values with `encoding: "plain"`, as the web does. There is no `data/crypto/` class. No legacy XOR records exist in production (spec §3.9).
- Amounts: write a Long for whole numbers and a Double otherwise, as the JS SDK does. Read numbers and numeric strings; anything else reads as 0.
- Dates are local-calendar `YYYY-MM-DD` strings. Never derive them from UTC.
- `settings` and `holdings` documents accept only the keys in the spec; never add fields to them.
- Port web logic exactly, quirks included, so totals agree (spec "Known web quirks").
- Never write a document shape the web app can't read.
- Readers are tolerant: any field except `uid` may be missing on old docs (spec §3.7, §3.9). Update existing docs with field-path `update(...)`, never a whole-document `set`.

## Workflow rules
- Work one feature slice at a time. Finish, build, and verify before starting the next.
- After every change, run `./gradlew assembleDebug`. Fix all errors and warnings you introduced.
- Run unit tests with `./gradlew testDebugUnitTest` for anything in `domain/` or `data/`.
- When a device or emulator is connected, run `./gradlew installDebug` to install.
- Keep business logic out of Composables.
- Don't add libraries beyond the stack above without asking.
- Never commit `google-services.json`, keystores, or signing passwords.
- Use small commits with clear messages, one per completed slice.

## Done means
- Builds cleanly
- Works in both light and dark themes
- Handles loading, empty, and error states
- Data round-trips with the web app: written on Android, readable on web, and the reverse
