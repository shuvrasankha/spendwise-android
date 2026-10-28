# Worker change for native Android push (decision D4)

Apply this in the **web repo** (`spendwise/worker/`), not here. It unblocks Roadmap Phase D (slice 26).
Everything below was written against `worker/src/push.js`, `index.js`, `webpush.js` and `schema.sql`
as of 2026-10-28.

## Why a change is needed

Today the Worker only knows browser Web Push:

- `/push/subscribe` rejects anything but a browser push URL (`validEndpoint`: `fcm.googleapis.com`,
  Mozilla, Apple, Windows). An Android app has an **FCM registration token**, not a URL.
- `sendPush` POSTs a VAPID-signed, `aes128gcm`-encrypted body to that URL. Native FCM needs a call to
  the **FCM HTTP v1 API** with an OAuth2 access token from a service account instead.

## Design: one table, one prefix

A native device is stored as a normal subscription whose endpoint is **`fcm:<token>`**.

- No schema change. `subscriptions`, `push_keys`, `bills` and `month_totals` and every cron query
  keep working, including the `JOIN push_keys`. For native rows `push_keys.p256dh` and `.auth` are
  `''` (the columns are `NOT NULL`), and only the verified email matters.
- One new function, `deliver(device, env, message)`, picks the transport: `fcm:` rows go to FCM v1,
  everything else to the existing Web Push path. Every send site calls `deliver`.
- The "cron only POSTs to push services" guarantee holds: an `fcm:` row is only ever sent to
  `https://fcm.googleapis.com/v1/projects/<FIREBASE_PROJECT_ID>/messages:send`, a fixed URL.

FCM payloads are sent as plain **data messages**, unencrypted. Web Push encrypts because the push
service is a third party. With FCM v1 the request goes over TLS straight to Google, which already
runs Firebase for this project. The Android app builds the notification itself, so the text and
look match the web's service worker (`sw.js`).

## 1. One-time setup (Firebase console + Wrangler)

1. Firebase console › Project settings › Cloud Messaging: make sure **Firebase Cloud Messaging API
   (V1)** is enabled.
2. Project settings › Service accounts › **Generate new private key** (the
   `firebase-adminsdk-…` account already has the `cloudmessaging.messages.create` permission).
3. Store the downloaded JSON as a Worker secret, then delete the file:
   ```sh
   cd worker
   npx wrangler secret put FCM_SERVICE_ACCOUNT < path/to/service-account.json
   ```
   Never commit it. `wrangler.toml` needs no change: `FIREBASE_PROJECT_ID` is already there.

## 2. `worker/src/push.js`

### 2a. Constants (next to `PUSH_HOSTS`)

```js
// A native Android app registers its FCM token as "fcm:<token>"; it is only ever sent to FCM v1's fixed URL
export const FCM_PREFIX = 'fcm:';
const FCM_TOKEN = /^[A-Za-z0-9_:-]{32,512}$/;
```

Also change the comment above `PUSH_HOSTS` to cover both kinds of endpoint.

### 2b. `validEndpoint`: accept `fcm:` tokens

```js
function validEndpoint(endpoint) {
  if (typeof endpoint === 'string' && endpoint.startsWith(FCM_PREFIX)) {
    return FCM_TOKEN.test(endpoint.slice(FCM_PREFIX.length));
  }
  try {
    const { protocol, hostname } = new URL(endpoint);
    return protocol === 'https:' && endpoint.length <= 1024 &&
      (PUSH_HOSTS.includes(hostname) || PUSH_HOST_SUFFIXES.some(suffix => hostname.endsWith(suffix)));
  } catch { return false; }
}
```

`/push/unsubscribe` uses the same check, so a native device can unsubscribe with no further change.

### 2c. `/push/subscribe`: store the email for native devices too

Replace the `push_keys` block:

```js
// Keys let this device receive message pushes for this email; older clients don't send them.
// A native (FCM) device needs no keys: its messages aren't Web Push encrypted.
const native = endpoint.startsWith(FCM_PREFIX);
const { p256dh, auth } = body.keys || {};
if (email && (native || (KEY.test(p256dh || '') && KEY.test(auth || '')))) {
  await env.DB.prepare(
    `INSERT INTO push_keys (endpoint, email, p256dh, auth) VALUES (?1, ?2, ?3, ?4)
     ON CONFLICT(endpoint) DO UPDATE SET email = ?2, p256dh = ?3, auth = ?4`
  ).bind(endpoint, email, native ? '' : p256dh, native ? '' : auth).run();
}
```

### 2d. FCM v1 sending (new, below `sendPush`)

`jose` is already a dependency. Add `importPKCS8` to its import line:
`import { importJWK, importPKCS8, SignJWT } from 'jose';`

```js
// An OAuth2 access token for FCM v1 from the service account (secret FCM_SERVICE_ACCOUNT), cached until near expiry
let fcmAuth;
async function fcmAccessToken(env) {
  if (fcmAuth && fcmAuth.expires > Date.now() + 60_000) return fcmAuth.token;
  const sa = JSON.parse(env.FCM_SERVICE_ACCOUNT);
  const jwt = await new SignJWT({ scope: 'https://www.googleapis.com/auth/firebase.messaging' })
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuer(sa.client_email)
    .setAudience('https://oauth2.googleapis.com/token')
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(await importPKCS8(sa.private_key, 'RS256'));
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: jwt }),
  });
  if (!res.ok) throw new Error(`FCM auth ${res.status}`);
  const { access_token, expires_in } = await res.json();
  fcmAuth = { token: access_token, expires: Date.now() + expires_in * 1000 };
  return access_token;
}

// The FCM v1 request body: a data-only message the app turns into a notification. The reminder has no
// text of its own (kind "reminder"); a message carries the same title/body/url/tag the browser gets.
export function fcmBody(token, message) {
  const data = message ? { kind: 'message', ...JSON.parse(message) } : { kind: 'reminder' };
  return {
    message: {
      token,
      data, // every value is a string, as FCM requires
      android: { priority: 'normal', ttl: message ? '86400s' : '3600s', ...(data.tag && { collapse_key: data.tag }) },
    },
  };
}

// Returns 'sent' | 'gone' | 'failed', like sendPush. 404 or UNREGISTERED: the app was uninstalled or the token rotated.
export async function sendFcm(token, env, message = null) {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${await fcmAccessToken(env)}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(fcmBody(token, message)),
  });
  if (res.ok) return 'sent';
  if (res.status === 404) return 'gone';
  const err = await res.json().catch(() => null);
  return err?.error?.details?.some(d => d.errorCode === 'UNREGISTERED') ? 'gone' : 'failed';
}

// One push to one device, by its kind. device: { endpoint, p256dh, auth }; message: a JSON string, or null for the reminder
export async function deliver(device, env, message = null) {
  if (device.endpoint.startsWith(FCM_PREFIX)) return sendFcm(device.endpoint.slice(FCM_PREFIX.length), env, message);
  return sendPush(device.endpoint, env, message && await encryptPush(message, device));
}
```

Only `404` and `UNREGISTERED` count as gone. Treating a plain `400 INVALID_ARGUMENT` as gone would
wipe every native device after a single payload bug.

### 2e. Use `deliver` at the four send sites

| Where | Today | Becomes |
|---|---|---|
| `pushToEmail` | `sendPush(device.endpoint, env, await encryptPush(message, device))` | `deliver(device, env, message)` |
| `sendDueReminders` | `sendPush(sub.endpoint, env)` | `deliver(sub, env)` |
| `sendBillAlerts` | `sendPush(row.endpoint, env, await encryptPush(billMessage(row), row))` | `deliver(row, env, billMessage(row))` |
| `sendMonthSummaries` | `sendPush(row.endpoint, env, await encryptPush(summaryMessage(row), row))` | `deliver(row, env, summaryMessage(row))` |

The rows already carry `endpoint`, `p256dh` and `auth`. Each call is already inside a `try` that
maps a throw to `'failed'`, so a missing secret or a failed FCM auth never stops the cron or drops a
subscription. `sendPush` stays exported and unchanged, so `push.test.mjs` keeps passing.

## 3. `worker/src/index.js`

No change, apart from the header comment: mention that `/push/*` also serves native FCM devices.

## 4. Tests (`worker/`, `npm test`)

Add to `push.test.mjs` (or a new `fcm.test.mjs` added to the `test` script):

1. `validEndpoint` via `/push/subscribe`: `fcm:` plus a 163-character token gives 200. `fcm:`,
   `fcm:short`, `fcm:` plus a token containing `/` or a space, and `fcm:` plus 600 characters give 400.
2. Subscribing a native device with a verified email writes `push_keys` with `''` keys. Without an
   email it writes only `subscriptions`.
3. `fcmBody(token, null)` returns `data: { kind: 'reminder' }` with ttl `3600s` and no collapse_key.
   `fcmBody(token, billMessage(…))` returns `kind: 'message'`, all data values strings, ttl `86400s`,
   and `collapse_key` equal to the tag.
4. `sendFcm` with a stubbed `fetch`:
   - 200 is sent.
   - 404 is gone.
   - 400 with `details: [{ errorCode: 'UNREGISTERED' }]` is gone.
   - 400 `INVALID_ARGUMENT` is failed.
   - 500 is failed.
   - The token request goes to `oauth2.googleapis.com/token` once, and is reused (cached) on the
     second send.
5. `deliver` sends an `fcm:` row to FCM and an `https:` row through `sendPush`. Assert on the
   fetched URL.
6. The cron with one `fcm:` subscription that returns 404: the subscription and its `push_keys`
   row are deleted (`forget`).

For tests, generate a throwaway RSA key in the test (`generateKeyPair('RS256', { extractable: true })`
then `exportPKCS8`) and put it in a fake `FCM_SERVICE_ACCOUNT`. Never use the real one.

## 5. Deploy and check

```sh
cd worker && npm test && npm run deploy
```

Then, with the Android build from slice 26 on a phone:

- Settings › Daily reminder on. A row with an `fcm:` endpoint appears:
  `npx wrangler d1 execute spendwise-push --remote --command "SELECT substr(endpoint,1,12), uid, remind_at, tz FROM subscriptions"`.
- Set the reminder a few minutes ahead. The reminder arrives within 15 minutes (the cron interval).
- Split a bill with your second, throwaway account's email. That account's phone gets
  "… split a bill with you".
- Web reminders still arrive in the browser (regression check).

## 6. What the Android side does afterwards (slice 26, this repo)

Listed here so both halves agree on the contract. It's not part of the Worker change.

- **Library:** add `firebase-messaging` from the BoM. That's a stack addition, which CLAUDE.md says to
  ask about; choosing D4 covers it.
- **Token:** a `FirebaseMessagingService`:
  - `onNewToken` sends `/push/subscribe {endpoint: "fcm:" + token, remindAt, tz}` when the reminder
    is on, after unsubscribing the previous token, which is kept in DataStore.
  - Reminder on/off in Settings calls subscribe/unsubscribe.
  - On log out and account deletion, call unsubscribe.
- **Messages** (`onMessageReceived`):
  - `kind: "reminder"` shows "Log today's spending" with "Add what you spent today. It only takes a
    few seconds." (`sw.js`).
  - `kind: "message"` shows `title`/`body` and uses `tag` as the notification tag, so it replaces
    rather than stacks.
  - `url` maps to a route: `index.html` → Dashboard, `debt.html` → Debts, `insights.html` → Insights,
    anything else → Dashboard.
- **Bills and months:** send `/push/bills` and `/push/months` the way `reminders.js › syncBills` and
  `syncMonthTotals` do (same keys, the same 50-bill cap, the currency sign). Without them, native
  devices get no bill or summary alerts unless the user also opens the web app.
- **One reminder only:**
  - While the device's `fcm:` subscription is stored, the local WorkManager reminder is off.
  - It turns back on when subscribing fails: a 409 for too many devices, offline, or a Worker error.
  - It also turns back on when notifications are blocked at the FCM level.
  - That way no user ever gets two reminders, or none.
- **Channels:** one notification channel for "Reminders" (the daily reminder, bills, summaries)
  and one for "Shared debts".

## Risks

- **The service-account secret** can send a push to any device of this Firebase project. It lives
  only in the Worker secret, and the Worker only sends to tokens that a verified user registered.
- **Data messages** are delayed by Doze on some devices. `priority: normal` is enough for a daily
  reminder; raise it to `high` only if debt pushes arrive late in practice.
- **Old Android builds** without slice 26 never register, so they are unaffected.
