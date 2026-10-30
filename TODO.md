# To do: publishing on Google Play

## Blockers (Play won't accept or approve without these)

- [x] **Privacy policy**: live at https://shuvrasankha.github.io/spendwise-android/privacy-policy.html (Pages on, matches the URL in Settings). Still to do: enter it in the Play Console. It must cover: account and money data
      stored in Firebase; numbers and questions (never names or notes) sent to the AI on Cloudflare; crash logs
      (Crashlytics).
- [x] **Account-deletion web page**: live at https://shuvrasankha.github.io/spendwise-android/delete-account.html. Still to do: enter its URL in the Play Console's Data safety form.
- [ ] **Release key**: create `upload.jks` and `keystore.properties` (README › Release), back the key up, then
      run `./gradlew bundleRelease`. Turn on Play App Signing when you upload.
- [ ] **versionCode**: check Play Console. If `com.spendwise.app` was ever uploaded, raise `versionCode` in
      `app/build.gradle.kts` above that build (it's 1 now).
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
- [ ] **Compose UI tests** for Add expense and sign-in (item 10, needs a working emulator or phone).

## Order

Privacy policy and deletion page → release key and bundle → Play signing SHA-1 in Firebase →
closed test (12 testers, 14 days) → production.
