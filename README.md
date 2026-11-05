<img src="docs/brand/spendwise-lockup.svg" alt="SpendWise" width="480">

# SpendWise for Android

A native Android app for [SpendWise](https://github.com/shuvrasankha/spendwise), the personal-finance
web app. Both apps share one Firebase project, so whatever you add on one shows up on the other.

## Features

- **Expenses**: add and edit expenses with category icons, split a bill, repeating bills and EMIs
- **Income**: add and edit income, see recent entries
- **Debts**: what you owe and are owed, with sharing, claims, part-payments and a by-person view
- **Dashboard**: the month at a glance, bills due, a score, goals, and day, week, month and year views
- **History**: search, filters and CSV export
- **Insights**: a forecast and cards for each period
- **Investments**: holdings and the money you've put in
- **Budgets and goals**, with an AI budget suggestion (one a month per account)
- **CSV import**, read with AI
- **AI your way**: SpendWise AI by default, or your own NVIDIA API key (see below)
- **App lock** with your fingerprint, face or screen lock
- **Home-screen widget** showing today's spending, with a + button to add an expense
- **Daily reminder**, notifications, and Play Store in-app updates
- Light and dark themes (they follow the system unless you choose one), with a choice of currency

## Your own AI key (BYOK)

By default, Ask, the coach, voice and receipt entry, CSV import and the budget suggestion use
SpendWise AI, which has daily limits. You can use your own NVIDIA API key instead:

1. Get a key at [build.nvidia.com](https://build.nvidia.com/).
2. In the app, open **Settings › AI provider**, paste the key and tap **Save key**.
3. Tap **Test connection**, then choose **My own API key** and agree to send requests to NVIDIA.

**What goes where:** with your own key, each AI request goes straight from your phone to NVIDIA,
under NVIDIA's terms. That means what you type or say, receipt photos, CSV rows, and the spending
and income figures the feature needs. Nothing goes to SpendWise's servers, and if NVIDIA returns an
error the app never falls back to SpendWise AI.

**The key:** it's encrypted with an Android Keystore key and stored on this phone only. It's never
backed up or synced, and it's deleted when you log out or delete your account.

**Limits and cost:** NVIDIA sets the rate limits, quotas, pricing and which models are available,
and these can change at any time. SpendWise can't see your usage or balance. The default model is
`nvidia/nemotron-3.5-super-vl-120b-a12b`; you can set a different text or vision model in the same
screen.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Coroutines and Flow · Firebase Auth and Firestore
(offline persistence on) · Credential Manager for Google Sign-In · Navigation Compose · DataStore ·
WorkManager. Charts are drawn with Compose Canvas.

minSdk 26 · targetSdk 37 · application ID `com.spendwise.app`

## Project layout

```
app/src/main/java/com/spendwise/app/
  data/      repositories, Firestore mappers, DataStore prefs
  domain/    plain Kotlin business logic, ported from the web app
  reminder/  daily reminder (WorkManager)
  widget/    home-screen widget
  ui/        Compose screens, one package per feature
  di/        Hilt modules
```

The specs live alongside the code:

- [`ANDROID_SPEC.md`](ANDROID_SPEC.md): every screen, flow and Firestore schema
- [`ROADMAP.md`](ROADMAP.md): how the app gets to parity with the web
- [`CLAUDE.md`](CLAUDE.md): project rules, including the data-compatibility rules

## Building

1. Install Android Studio (latest stable), with JDK 17 or newer.
2. Download `google-services.json` from the SpendWise Firebase project and put it in `app/`.
   Don't commit it; it's gitignored.
3. Add the debug SHA-1 to Firebase so Google Sign-In works.
4. Build and test:

```sh
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
./gradlew installDebug           # install on a connected device
```

## Working with the web app

The web app is live and shares the same Firestore data, so collection names, field names, date
formats (local `YYYY-MM-DD`) and amount types must match it exactly. Update documents with
field-path updates, never a whole-document `set`. See `CLAUDE.md` and spec §3 before you touch
anything in `data/`.

## Release

The release build is signed with an upload key that never goes into git.

1. Create the key once and keep it backed up (losing it means asking Play to reset it):

   ```sh
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Next to `settings.gradle.kts`, create `keystore.properties`:

   ```properties
   storeFile=upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

3. Raise `versionCode` in `app/build.gradle.kts` above the last build on Play, then build the bundle:

   ```sh
   ./gradlew bundleRelease          # app/build/outputs/bundle/release/app-release.aab
   ```

Turn on Play App Signing when you upload the first bundle, so Google holds the app signing key.

### GitHub release (automatic)

Pushing a `v*` tag runs `.github/workflows/release.yml`: it builds the signed APK and bundle, checks the
APK isn't debug-signed, and publishes a GitHub release with `SpendWise.apk` (what the in-app updater
downloads). The bundle for Play is kept as an artifact on the run.

One-time setup (run in Terminal; `gh secret set` asks for the password without echoing it):

```sh
base64 -i upload.jks | gh secret set UPLOAD_KEYSTORE_BASE64
gh secret set UPLOAD_KEYSTORE_PASSWORD
base64 -i app/google-services.json | gh secret set GOOGLE_SERVICES_JSON_BASE64
```

`UPLOAD_KEY_PASSWORD` is only needed if the key's password differs from the keystore's.

Each release:

1. Raise `versionCode` and set `versionName` in `app/build.gradle.kts` (the tag must be `v` + versionName),
   commit and push to `main`.
2. Write the notes in a file and tag with them (`--cleanup=verbatim` keeps `##` headings):

   ```sh
   git tag -a v1.6.7 --cleanup=verbatim -F notes.md
   git push origin v1.6.7
   ```

   A tag without a message gets GitHub's generated notes.
