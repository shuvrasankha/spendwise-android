# SpendWise Android: BYOK AI implementation plan

**Goal:** let users connect their own AI API key, starting with the NVIDIA API Catalog (hosted NIM),
and use it for SpendWise's existing AI features. SpendWise AI (the Cloudflare Worker) stays the
default.

Repository: https://github.com/shuvrasankha/spendwise-android

> Describe "free API key" carefully. Providers may offer free developer access, trial credits or
> limited quotas, and access, model availability, rate limits and pricing can change. SpendWise
> must never promise unlimited or permanently free usage.

Scope note: AI already ships on Android through the Worker (ROADMAP Phase C, decisions D1 and D5).
This plan is ROADMAP decision **D6**. It is Android only: no change to the web app or the Worker.

## 1. Product scope

### User flow

1. Open **Settings → AI Provider**.
2. Choose **SpendWise AI** (default) or **My own API key**.
3. Pick a provider (NVIDIA only at first).
4. Keep the suggested models or change them (§4.2).
5. Enter the API key; it is saved encrypted on this device.
6. Test the connection.
7. Agree to the provider consent (§7.2), then activate.
8. Existing AI features now use that provider.
9. Switch back to SpendWise AI, or remove the key, at any time.

### Providers

| Priority | Provider | Integration |
|---|---|---|
| P0 | NVIDIA API Catalog (hosted NIM) | OpenAI-compatible chat completions |
| P1 | Google Gemini | Native Gemini adapter, unless a currently documented compatible endpoint is chosen |
| P1 | Groq | OpenAI-compatible adapter |
| P2 | OpenRouter | OpenAI-compatible adapter; model availability and pricing vary |

Ship NVIDIA alone first. Add the others only after the gateway and key storage are stable.

## 2. NVIDIA facts to verify before coding

From NVIDIA's API reference, to be re-checked on the day of implementation:

- Base URL `https://integrate.api.nvidia.com/v1`, endpoint `POST /chat/completions`.
- `Authorization: Bearer <user API key>`.
- OpenAI Chat Completions request and response shape for supported models.
- Model IDs come from the current API Catalog. Don't assume any model is available to every key.
- **Vision:** confirm which hosted models accept `image_url` parts with a base64 `data:` URL, and the
  image size limit. Receipt reading depends on it (§4.2).
- **Rate limits:** the per-minute and per-day limits on a free developer key. CSV import sends
  several requests at once (§7.3).
- The hosted API Catalog is not the same as self-hosted NIM containers (host, auth, models and
  entitlements differ). Only the hosted catalog is in scope.

References:
- https://docs.api.nvidia.com/nim/reference/llm-apis
- https://docs.api.nvidia.com/nim/docs/api-quickstart
- https://build.nvidia.com/

### Checked 2026-11-05 (Phase 0.4)

- **Confirmed:** `POST https://integrate.api.nvidia.com/v1/chat/completions`, `Authorization: Bearer <key>`,
  `Accept: application/json` (llm-apis reference, updated 2026-10-23; build.nvidia.com sample).
- **Confirmed on the hosted list:** `meta/llama-3.3-70b-instruct` (Ask, Coach, Budget) and
  `qwen/qwen2.5-coder-32b-instruct` (Voice, CSV).
- **Not confirmed:** `meta/llama-4-scout-17b-16e-instruct` has a build.nvidia.com page (input "Text, Image"),
  but it isn't on the llm-apis hosted list and its reference page returns 404. Nothing in the docs shows
  `image_url` with a base64 `data:` URL, or gives an image size limit. Test it with a real key in 3.7
  before it becomes the default vision model.
- **Not documented:** the error body shape and the free-key rate limits (these may be in the Trial Terms
  PDF). Map errors by HTTP status only (§3.3 of the steps).

### Live test with a real key, 2026-11-05 (steps 3.7)

- **The documented models are gone.** `meta/llama-3.3-70b-instruct`, `qwen/qwen2.5-coder-32b-instruct` and
  `llama-4-maverick` return **410 Gone** (end of life); `llama-4-scout` returns 404. Many models that
  `/v1/models` lists return 404 "Not found for account" or hang past 60 s. The docs list lags the service.
- **Default for all six tasks:** `nvidia/nemotron-3.5-super-vl-120b-a12b` (text and vision), sent with
  `"chat_template_kwargs":{"enable_thinking":false}`. It's a reasoning model: with thinking on, the hidden
  reasoning spends `max_tokens` and Ask (200) comes back empty. With thinking off, every task answered
  in about 1 s and each feature's parser read it, receipt image included (base64 `data:` URL works).
- **Budget:** the reply parsed, but the plan failed `aiPlanHolds` on the test facts, so the device's
  own plan would be shown. That fallback is the intended safety net; watch for it in manual checks.
- **Errors seen:** bad key → 403 `{"title":"Forbidden","detail":"Authorization failed"}`; retired model →
  410; unknown or unentitled model → 404. 410 now maps to `ModelNotFound`.
- With `max_tokens` 1, content is `null` (finish `length`); the connection test already treats any 2xx as a pass.

## 3. Current code (verified against `main` at 219c923)

### 3.1 The Worker client

`data/worker/WorkerClient.kt`:
- `WorkerClient.post(path, body, readTimeoutMs)` sends a JSON POST to `WORKER_URL` with the
  Firebase ID token and returns `WorkerResponse(status, body)`. It logs the path and status only.
- `aiRequest(...)` / `aiRequestOf(...)` build an **OpenAI-style** body: `model`, `messages`,
  `max_tokens`, `temperature`, optional `top_p`.
- `aiContent(body)` reads `choices[0].message.content`.

So the wire format already matches NVIDIA's. The NVIDIA adapter needs a different URL and auth
header, and the request and response helpers can be reused as they are.

### 3.2 AI call sites

| # | Feature | File | Model constant (today) | Consent | Quota |
|---|---|---|---|---|---|
| 1 | Ask | `ui/assistant/AssistantViewModel.kt:225` | `ASK_AI_MODEL` = `@cf/meta/llama-3.3-70b-instruct-fp8-fast` (`domain/Ask.kt`) | `AiConsent.ensure()` | `AiAllowance` ASK, 10/day |
| 2 | Coach | `ui/assistant/AssistantViewModel.kt:251` | `COACH_AI_MODEL` = same llama 3.3 70B (`domain/Coach.kt`) | `AiConsent.ensure()` | `AiAllowance` COACH, 5/day |
| 3 | Voice | `ui/expenses/VoiceViewModel.kt:142` | `VOICE_AI_MODEL` = `@cf/qwen/qwen2.5-coder-32b-instruct` (`domain/Voice.kt`) | **none** | `AiAllowance` VOICE, 20/day shared with receipt |
| 4 | Receipt | `ui/expenses/VoiceViewModel.kt:133→142` | `RECEIPT_AI_MODEL` = `@cf/meta/llama-4-scout-17b-16e-instruct`, **vision**, sends `image_url` | **none** | `AiAllowance` VOICE |
| 5 | CSV import | `ui/settings/CsvImportViewModel.kt:120` | `CSV_AI_MODEL` = qwen2.5-coder 32B (`domain/CsvImport.kt`) | own flag, `prefs.csvConsent` | `AiAllowance` CSV, 3/day, counted **once per import**; chunks run **in parallel**; 429 → `RATE_LIMITED`, 503 → `MODEL_LOADING` |
| 6 | Budget suggestion | `ui/dashboard/PlansViewModel.kt:159` | `BUDGET_AI_MODEL` = llama 3.3 70B (`domain/BudgetSuggest.kt`) | **none** | not `AiAllowance`: one AI plan a month (`prefs.aiBudgetMonth`), cached in `prefs.aiBudgetPlan`; any failure falls back to the device's own plan |

Also:
- `ui/insights/InsightsViewModel.kt` imports `WorkerClient`, `AiAllowance` and `AiConsent` but
  never uses them. Remove the imports in Phase 1.
- `data/worker/PushSync.kt`, `PushRegistry.kt` and `WorkerPush.kt` use the Worker for push, not
  AI. They are out of scope and always stay on the Worker.
- Behind all of this, the Worker allows 100 AI calls a day per account, shared by web and Android
  (`domain/AiLimits.kt`).

### 3.3 Backup and storage

- The manifest sets `allowBackup="false"`. `backup_rules.xml` and `data_extraction_rules.xml`
  exclude every domain from cloud backup and device transfer. A new key file is excluded
  automatically; Phase 7 only needs to confirm this.
- `DevicePrefs` (DataStore) already holds AI consent and the counters. Non-secret BYOK settings
  go there. The key does not.

Phase 0 re-checks this section on the current branch before any code changes.

## 4. Architecture

### 4.1 Gateway

ViewModels stop calling `WorkerClient` for AI. They call one gateway, which knows nothing about
URLs, headers or JSON shapes.

```text
AI ViewModels (Ask, Coach, Voice, Receipt, CSV, Budget)
        |
        v
    AiGateway  ── consent check, quota rule per feature, model choice
        |
        v
 AiProviderManager
        |
        +── WorkerAiProvider    (wraps WorkerClient; unchanged contract)
        +── NvidiaProvider      (OpenAI-compatible; reuses aiRequestOf / aiContent)
        +── later: GeminiProvider, OpenAiCompatibleProvider (Groq, OpenRouter)
```

A sketch only; adapt it to the existing code:

```kotlin
enum class AiTask(val needsVision: Boolean) { ASK(false), COACH(false), VOICE(false), RECEIPT(true), CSV(false), BUDGET(false) }

data class AiCall(
    val task: AiTask,
    val messages: List<Pair<String, JsonElement>>,
    val maxTokens: Int,
    val temperature: Double,
    val topP: Double? = null,
    val readTimeoutMs: Int? = null,
)

sealed interface AiResult {
    data class Ok(val content: String) : AiResult
    data class Failed(val error: AiError) : AiResult   // §8
}

interface AiProvider {
    suspend fun complete(model: String, call: AiCall): AiResult
    suspend fun testConnection(model: String): AiResult
}
```

- Prompts, temperatures, token limits and each feature's reply parser stay exactly as they are.
- The gateway returns the reply text; each feature keeps parsing it as it does today.
- `AiError` keeps enough detail for features that react to status codes today. CSV maps 429 and
  503 to its own wording.
- `WorkerClient.post` keeps its signature, because the push code uses it too.

### 4.2 Models, chosen per task

One user-entered model ID can't replace six tuned models, and a text-only model would break
receipt reading. So:

- **Worker mode:** each task keeps its current `@cf/...` constant. No change.
- **BYOK mode:** each provider ships a **default model per task**, picked from the verified
  catalog (§2): a strong instruct model for Ask, Coach and Budget, a model that holds strict JSON
  for Voice and CSV, and a vision model for Receipt.
- Settings shows the defaults and offers one optional **"Advanced: text model"** override, used for
  all text tasks, plus an optional vision-model override. Each choice is checked with a test
  call before it's saved.
- **Receipt reading is turned off in BYOK mode** when no vision-capable model is set. It says so in
  the UI ("Receipt scan needs a vision model") and never fails quietly.
- Ask expects strict JSON at temperature 0, and CSV expects long JSON replies (4096 tokens). Run
  each task's parser on a real NVIDIA reply during the Phase 3 manual test. If a default model
  can't hold the format, change the default model rather than the prompt.

### 4.3 Cancellation

`HttpURLConnection` doesn't stop when its coroutine is cancelled. Both providers run the request
inside `suspendCancellableCoroutine` or similar, and call `disconnect()` from
`invokeOnCancellation`. Then switching provider or leaving the screen stops a request in progress.

## 5. Key storage (critical)

The app holds financial data, so a user's API key is treated as a secret.

- The key stays on this device only.
- Encrypt it with an **Android Keystore AES-256 key** (`AES/GCM/NoPadding`, a random IV each
  time, alias `spendwise_byok_v1`). Platform APIs only: no new library. `EncryptedSharedPreferences`
  (security-crypto) is deprecated and would be a new dependency, so don't use it.
- Store the IV and ciphertext in one file under `noBackupFilesDir`, one per provider. The backup
  rules already exclude every domain.
- Never put the plaintext key in DataStore, Firestore, the Firebase profile, logs, crash reports,
  analytics, screenshots, source code or test fixtures.
- DataStore holds only non-secret settings: mode, provider ID, model overrides, BYOK consent flag.
- After saving, never refill the text field with the decrypted key. Show "Key saved" with a mask
  such as `nvapi-••••1a2b`. Store the last 4 characters in DataStore at save time, so the mask
  doesn't need a decrypt.
- Replace and remove are supported. Removing deletes the file, deletes the Keystore entry, clears
  the mask and switches back to SpendWise AI.
- If decrypting fails (`KeyPermanentlyInvalidatedException`, `AEADBadTagException`, a missing
  alias): delete the file and the alias, switch back to SpendWise AI, and show "Your saved key
  couldn't be read. Enter it again." This is shown, never silent.
- The Keystore key needs no user authentication. The app lock already protects the app.
- The key never appears in exception messages or logs. Provider log lines follow `WorkerClient`:
  path and status only.
- The key is sent only to the chosen provider's host, never to the Worker or any other service.

If any of this can't be done safely with platform APIs, stop and ask before adding a dependency.

## 6. Settings UI

An **AI Provider** section in Settings, built with the existing Compose and Material 3 conventions
and the design rules in `CLAUDE.md`.

Controls:
- Mode: SpendWise AI (default) / My own API key.
- Provider: NVIDIA. Later providers appear only once they're built.
- Models: the defaults per task, with the optional text and vision overrides (§4.2).
- API key: a password-style field, Save, and the masked "Key saved" status.
- Test connection: progress, then success or a safe error message (§8).
- Activate. Replace key / Remove key. Switch back to SpendWise AI.

Rules:
- Existing users stay on SpendWise AI after the upgrade. BYOK starts off, with no key saved.
- BYOK can't be activated until the provider, models and key have passed a test call and the
  user has agreed to the provider consent.
- Explain that the provider controls usage, free quotas, billing and availability.
- Explain that prompts and the related financial figures go **directly** to the chosen provider.
- Don't suggest that SpendWise can see the user's provider balance or usage.
- Never show raw provider response bodies or authorization details.
- Never switch provider, or where data is sent, without telling the user.

## 7. Routing, consent and quotas

### 7.1 SpendWise AI (Worker) mode: unchanged

- The same Firebase auth, Worker URL, request format and per-task models.
- The same consent behaviour at each call site, as listed in §3.2, gaps included. Voice, Receipt
  and Budget have no consent check on Android today. Closing that gap is a web-parity question,
  not part of BYOK. Raise it in Phase 0, but don't change Worker mode here.
- The same quotas: `AiAllowance` for Ask, Coach, Voice and CSV, and the monthly limit for Budget.

### 7.2 BYOK mode: consent

- A **separate consent for each provider**, stored in DataStore and keyed by provider ID (e.g.
  `byok_consent_nvidia`). It's asked when activating, and asked again if the provider changes.
- It covers **all six tasks**, including Voice, Receipt and Budget, which have no consent today.
  Data going to a third party the user picked needs their explicit agreement.
- The wording says plainly that the request and the related figures go to the chosen provider
  under that provider's terms, not to SpendWise.
- The existing `AiConsent` and `csvConsent` flags still apply as well. BYOK consent is added on
  top and never replaces them.

### 7.3 BYOK mode: routing and quotas

- Calls go straight from the phone to the provider over HTTPS. **They never go through the Worker.**
- **No silent fallback to the Worker.** If BYOK fails, the feature shows the error (§8).
  Budget suggestion already uses the device's own plan when the AI fails; that's still allowed,
  because it sends nothing anywhere.
- No `AiAllowance` counting. Those caps protect the Worker's shared 100-a-day budget, which BYOK
  doesn't use.
- **Budget's one-plan-a-month rule stays in both modes.** It's a product rule (the plan stays put
  for the month), not a Worker cost control. Document this distinction in the code.
- **CSV import in BYOK mode** runs chunks with limited concurrency (2 at a time) so a free key
  doesn't hit its per-minute limit. Worker mode keeps its current fully parallel behaviour.
- Data sent stays as small as it is today. The existing rule that no descriptions, notes or names
  go to the AI paths applies unchanged to every provider. No email, uid or account IDs are added.

## 8. Errors

| Case | Message |
|---|---|
| 401 / 403 | API key rejected; check the key |
| 429 / quota exhausted | Provider limit reached; check your provider account or try later |
| 404 / unknown model | Model unavailable; check the model ID |
| 503 / model loading | Provider is busy; try again shortly |
| Network error | Could not connect; check your internet |
| Timeout | Provider took too long; try again |
| Empty or malformed reply | Provider returned an unsupported response |
| Safety or content block | Provider could not complete this request |
| Secure-storage failure | Key could not be stored securely |
| Receipt with no vision model | Receipt scan needs a vision model; set one in Settings |

Never show stack traces, headers, tokens, request bodies or full provider error bodies. In Worker
mode, each feature keeps its current wording.

## 9. Testing

### Unit tests (`testDebugUnitTest`)

- The default mode is the Worker, for new installs and upgrades alike.
- Routing: Worker mode never calls the BYOK provider, and BYOK mode never calls the Worker.
- Push calls always use the Worker, in both modes.
- `AiAllowance` is counted only in Worker mode. Budget's monthly rule applies in both modes.
- BYOK refuses to run without provider consent. Changing provider clears that consent.
- No fallback from BYOK to the Worker for any task.
- Each task uses its own model. Receipt is refused in BYOK mode without a vision model.
- Key storage: encrypt and decrypt, replace, remove, and recovery from an invalidated key.
  Keystore code sits behind an interface so the tests can use a fake.
- The plaintext key never appears in any DataStore value.
- NVIDIA requests are built and replies parsed correctly, including empty and malformed replies.
- 401, 403, 404, 429, 503, timeout and network errors map to the right `AiError`.
- Cancelling a request in progress disconnects it.
- CSV in BYOK mode never has more than 2 chunks in flight.

### Manual checks

- Set up a developer-owned NVIDIA key locally, never committed.
- Test connection, then run each of the six tasks once and check each reply parses.
- Check that no request carrying the key goes to the Worker or Firebase (proxy or logcat).
- Search logcat for the key, or any part of it, during setup, use and errors.
- Try an invalid key, a model ID that doesn't exist, and a quota-exhausted key.
- Switch back to the Worker; remove the key and restart the app.
- Upgrade from 1.6.7 and confirm the app stays on the Worker.
- Check both light and dark themes, and the loading, empty and error states.

CI uses mocked HTTP only. Never commit a real key or print one in test or build output.

## 10. Phases

Each phase is one focused commit.

| Phase | Work | Done when |
|---|---|---|
| 0 | Re-check §3 on the current branch: call sites, consent, quotas, backup rules. Report anything that has drifted. Raise the Voice, Receipt and Budget consent gap (§7.1) as a question. | §3 confirmed, or the differences reported |
| 1 | Add `AiGateway` and `WorkerAiProvider`. Move all six call sites onto it, still Worker only. Remove the unused imports in Insights. | Every AI feature behaves exactly as before; tests pass |
| 2 | Keystore key storage and BYOK settings in DataStore. | No plaintext key in prefs, cloud or logs; storage tests pass |
| 3 | `NvidiaProvider`, per-task defaults, connection test, cancellation. | A valid NVIDIA key passes the test, and each task's parser accepts a real reply |
| 4 | Settings UI: mode, provider, models, key, test, consent, activate, remove, switch back. | The full setup lifecycle works in both themes |
| 5 | BYOK routing rules: consent, quota rules, CSV concurrency, receipt gating, no fallback. | The routing tests in §9 pass |
| 6 | Gemini, Groq and OpenRouter, one at a time. | Each has verified API docs and its own tests |
| 7 | Security review: data sent, logs, backup rules, a grep of the diff for secrets. | The security checks in §9 pass |
| 8 | All tests, debug and release builds, the manual checks, README. | The release build succeeds |

## 11. Definition of done

- [ ] SpendWise AI remains the default for current and new users.
- [ ] A user can set an NVIDIA key and models, with a working connection test and safe errors.
- [ ] All six AI tasks go through the gateway; push stays on the Worker.
- [ ] Each task uses its own model; receipt reading needs a vision model in BYOK mode.
- [ ] The key is encrypted with an Android Keystore key and stored under `noBackupFilesDir`.
- [ ] The key is absent from DataStore, Firebase, logs, crash reports and backups.
- [ ] BYOK calls go straight to the provider, without `AiAllowance`; Budget's monthly rule stays.
- [ ] Provider consent is required for all six tasks and is asked again when the provider changes.
- [ ] No silent fallback to the Worker.
- [ ] The user can switch back and remove the key.
- [ ] Nothing promises unlimited or free provider usage.
- [ ] Tests, the debug build and the release build pass.

---

# Claude Code prompt

Run from the repository root.

```text
Implement BYOK AI in this repository. Read BYOK_IMPLEMENTATION_PLAN.md first; it is the
specification (ROADMAP decision D6). Follow BYOK_IMPLEMENTATION_STEPS.md phase by phase,
one commit per phase.

GOAL
Keep SpendWise AI (Worker) as the default. Let users set their own provider key, starting with
the NVIDIA API Catalog. Route all six AI tasks (Ask, Coach, Voice, Receipt, CSV, Budget) through
one gateway. Push stays on the Worker.

RULES
- §3 lists the verified call sites. Re-check them first (Phase 0) and report any drift.
- Keep prompts, parsers, per-task Worker models and Worker-mode consent and quotas unchanged.
- In BYOK mode, use per-task default models (§4.2). Turn off receipt reading without a vision model.
- Provider consent is required for all six tasks in BYOK mode, and asked again when the provider changes.
- No AiAllowance in BYOK mode. Budget's monthly rule stays in both modes. CSV runs 2 chunks at a time in BYOK mode.
- Never fall back from BYOK to the Worker without telling the user.
- Key: Android Keystore AES-GCM, ciphertext in noBackupFilesDir, no new library, never in
  DataStore, logs or Firestore.
- Cancelling a coroutine must disconnect its HttpURLConnection.
- Check NVIDIA's current endpoint, auth, models (including vision) and rate limits in the
  official docs. Never hardcode an unverified model.
- Android only; no change to the web app or the Worker. No new dependencies without asking.
- Follow CLAUDE.md: one phase per commit; assembleDebug and testDebugUnitTest after each.

STOP AND ASK
Before adding a dependency, changing Worker-mode behaviour (the consent gap included), storing
keys anywhere but on the device, routing BYOK through the Worker, weakening consent, sending more
financial data, or allowing custom endpoints.

FINAL REPORT
Providers supported, call sites migrated, the per-task models, the key-storage design, consent,
quota and fallback behaviour, files changed, the exact test and build commands with their
results, known limits and deferred work. Report failures plainly.
```
