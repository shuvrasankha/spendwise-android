# To do: publishing on Google Play

## Blockers (Play won't accept or approve without these)

- [x] **Privacy policy**: live at https://shuvrasankha.github.io/spendwise-android/privacy-policy.html (Pages on, matches the URL in Settings). Still to do: enter it in the Play Console. It must cover: account and money data
      stored in Firebase; numbers and questions (never names or notes) sent to the AI on Cloudflare; crash logs
      (Crashlytics).
- [x] **Account-deletion web page**: live at https://shuvrasankha.github.io/spendwise-android/delete-account.html. Still to do: enter its URL in the Play Console's Data safety form.
- [ ] **Release key**: create `upload.jks` and `keystore.properties` (README › Release), back the key up, then
      run `./gradlew bundleRelease`. Turn on Play App Signing when you upload.
- [ ] **versionCode**: check Play Console. If `com.spendwise.app` was ever uploaded, raise `versionCode` in
      `app/build.gradle.kts` above that build (it's 13 now, as of 1.6.5).
- [ ] **Play Console forms**: Data safety (account info, financial info, crash logs, encrypted in transit,
      deletion), Content rating, Target audience (not children), Financial features (budgeting/expense
      tracking, no lending).
- [ ] **Closed testing**: personal developer accounts created after November 2023 need at least 12 testers
      for 14 days before production.

## Before launch

- [ ] **Store listing**: 512×512 icon (from `docs/brand/spendwise-icon.svg`), 1024×500 feature graphic,
      at least 2 phone screenshots, short and full descriptions.
- [ ] **Crashlytics**: turn it on in the Firebase console.
- [ ] **Play app signing SHA-1 → Firebase** (Play Console › App integrity), or Google Sign-In fails in the
      Play-installed app.
- [ ] **Check on a real phone**: Quick Settings tile, 4×2 month widget, budget alerts, and the release
      build's sign-in (not yet seen working on a device; the emulator kept crashing).
- [ ] **Compose UI tests** for Add expense and sign-in (item 10, needs a working emulator or phone; a
      Gradle Managed Device in CI is an alternative).

## Security and privacy (do before launch: it's a finance app)

- [ ] **Backup rules**: `allowBackup="true"`, but `data_extraction_rules.xml` is still the Android template,
      so Google backup can copy the Firestore offline cache and the DataStore prefs (including app-lock state)
      to a new phone. Exclude the Firestore cache and the app-lock and AI-consent keys (and the same in
      `backup_rules.xml` for API < 31), or set `allowBackup="false"`.
- [ ] **Hide content in Recents**: when app lock is on, call `setRecentsScreenshotEnabled(false)` (API 33+)
      and use `FLAG_SECURE` below that, so the app switcher doesn't show balances. Decide whether it should
      also apply when app lock is off.
- [ ] **Firebase App Check**: Play Integrity provider (debug provider for debug builds). Start in monitor
      mode only. The web app shares the project and needs reCAPTCHA Enterprise before enforcement. Have
      `WorkerClient` send the App Check token so the Worker can verify it later (Worker change is in the web
      repo).

## Performance

- [x] **Shared live flows**: expenses, income, debts, holdings and settings each map their snapshot once
      and share it (`SharedLive` in `data/Live.kt`: app scope, `WhileSubscribed(5_000)`, replay 1, cache
      dropped on stop, restarted on user change, next collector after an error retries). The `serverOnly`
      expenses read (budget alerts, push sync) stays its own listener.
- [x] **Paginate History**: the next 50 rows load automatically as the list nears its end (no button).
      The Firestore read stays one full `uid` listener: counts, totals, chips, search and CSV export need
      every entry, and spec §0.6 rules out composite indexes. Filtering runs off the main thread.
- [x] **Skeleton loading**: History and Dashboard show flat skeleton rows in the surface colour while
      loading (`SkeletonBar`, `SkeletonRows` in `ui/components/Display.kt`; no shimmer).
- [x] **List hygiene**: every `items()` has a stable `key` (Assistant and CSV import added); the currency
      picker is a `LazyColumn` (`SwSheet(scroll = false)`); `HistoryItem`, `HistoryView` and `AssistantRow`
      are `@Immutable`, and `domain/` models are stable through `compose_stability.conf`.
- [x] **Baseline profile**: `:baselineprofile` module (cold start, Dashboard, History scroll, Add expense;
      it never saves). Sign in on the emulator, then `./gradlew :app:generateBaselineProfile`; re-run after
      big UI changes. `StartupBenchmark` compares cold start with and without it. Still to do: generate it
      (use a hardware-graphics emulator) and commit `app/src/release/generated/baselineProfiles/`.
- [x] **Firebase Performance Monitoring**: release builds only (`perf` placeholder, no instrumentation in
      debug). Custom traces: `expenses_first_load`, `history_filter`. Still to do: mention it in the privacy
      policy and the Data safety form (app performance data), and turn it on in the Firebase console.
- [ ] Later, needs the Worker (web repo): stream AI replies token by token instead of `readText()`; send
      receipt photos as WebP instead of JPEG, if the AI endpoint accepts it.

## Quality

- [ ] **CI**: `.github/workflows/android.yml` running `./gradlew assembleDebug testDebugUnitTest lint` on
      push and PR, with Gradle caching. Check how the build behaves without `google-services.json`.
- [ ] **Accessibility**: Canvas charts are silent in TalkBack. Add a `semantics { contentDescription }`
      summary to each. Fix meaningful icons with `contentDescription = null`, check 48dp touch targets and
      200% font scale.
- [ ] **Localization (optional)**: about 100 strings are hardcoded in `Text("…")`. Move them to
      `strings.xml` only if Hindi or Bengali is planned.

## Order

Privacy policy and deletion page → release key and bundle → Play signing SHA-1 in Firebase →
closed test (12 testers, 14 days) → production.