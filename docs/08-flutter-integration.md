# 08 · Flutter Integration Guide

How the OneDay app talks to the backend. The contract is **`docs/api/openapi.json`** (OpenAPI 3.1): 211 operations in 43 groups.
- It is generated from the code.
- A test fails if the two drift, so the file is always current.
- To accept an API change, run `ONEDAY_UPDATE_OPENAPI=1 ./mvnw test -Dtest=OpenApiContractTest`.

## 1. Generate the Dart client

```bash
# openapi-generator (Docker or the npm wrapper); dart-dio gives interceptors for auth refresh and retries.
npx @openapitools/openapi-generator-cli generate -i docs/api/openapi.json -g dart-dio \
  -o packages/oneday_api --additional-properties=pubName=oneday_api,nullableFields=true
```

Each backend controller becomes one API class (`TimeCapsuleApi`, `ChatApi`, `E2eeApi`, …). Operation ids are stable, e.g. `timeCapsuleSeal`, `chatSend`, `liveJoin`. A few things to know:
- The contract is generated with the test profile, so it also lists dev-only routes (`/dev-media/**`) and the payment webhook. The app never calls those.
- Base URL: `http://10.0.2.2:8080` from the Android emulator, `http://localhost:8080` from the iOS simulator.

Run the backend locally:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # needs MySQL on localhost:3306
```

The `dev` profile simulates every vendor: SMS codes and pushes are printed in the log, and liveness accepts `dev-pass`.

## 2. Sign-in and tokens

| Step | Call | Notes |
|---|---|---|
| Sign up | `POST /auth/register` (email, password, date of birth, display name, `consentVersion`) | Under-18s are refused |
| Phone login (India default) | `POST /auth/otp/request` → `POST /auth/otp/verify` | |
| Verify (liveness) | `POST /verification/liveness` `{sessionToken}` | Returns a new token with `verified=true`. Contact features need it. |
| Refresh | `POST /auth/refresh` `{refreshToken}` | |
| Sign out | `POST /auth/logout`, `POST /auth/logout-all` | |

- **Access tokens** last **15 minutes**. Refresh before expiry.
- **Refresh tokens rotate on every use.** Store the newest one in secure storage (`flutter_secure_storage`). Replaying an old refresh token ends the session on every device, as theft detection.
- **Refresh coordination:** use a single-flight refresh in a Dio interceptor. On `401` refresh once, then retry once. Two parallel refreshes look like theft.
- `GET /auth/sessions` lists sign-ins (for "signed in on other devices").

## 3. Errors

Every error is an RFC 9457 problem detail:

```json
{"status":409,"code":"CONSENT_WITHDRAWN","detail":"…user-facing text…","requestId":"…"}
```

- Switch on `code`, never on `detail`. `detail` is written to be shown to the user.
- Show `requestId` in "contact support".
- Some codes carry extra fields:
  - `EMPATHY_CHECK` has `tone`;
  - `DEVICE_LIST_MISMATCH` has `missing` and `stale`;
  - `DEVICE_EXISTS` has `deviceId`.

| Code | App behaviour |
|---|---|
| `VERIFICATION_REQUIRED` / `REVERIFICATION_REQUIRED` (403) | Open the liveness flow |
| `EMPATHY_CHECK` (422) | Show `detail` with **Edit** and **Send anyway**. Resend with `sendAnyway: true` and a **new** `Idempotency-Key`. |
| `CONSENT_WITHDRAWN` (409) | Deep-link to Privacy settings (`GET /consents`) |
| `PACING_PAUSE` / `PACING_SLOW_DOWN` (429) | Show `detail` and disable send. No retry loop. |
| `DEVICE_NOT_TRUSTED` (403) | Attestation failed; see §6 |
| `E2EE_REQUIRED` (409) | The chat is encrypted; use the encrypted send (§8) |
| `*_RATE_LIMIT`, `TOO_MANY_*` (429) | Back off. Don't auto-retry. |

## 4. Retries and idempotency

- Send `Idempotency-Key: <uuid>` on every POST that creates something: messages, signals, plans, reports, capsules, posts.
- On a network failure, retry with the **same** key and body. The server replays the first response (header `Idempotent-Replayed: true`) for 24 h.
- A changed body needs a new key.

## 5. Real time (WebSocket)

- **Connect:** STOMP at `ws(s)://<host>/ws`, using the `stomp_dart_client` package. Send `Authorization: Bearer <access token>` in the CONNECT headers.
- **Subscribe:** only to `/user/queue/events`; anything else closes the socket.
- **Receive-only:** all writes go over HTTP.
- **When it closes:** the socket closes at token expiry or sign-out. Reconnect with a fresh token, then re-fetch the screen's data: events are best-effort, and HTTP is the source of truth.
- **Envelope:** `{"type": "...", "data": {...}}`.

| `type` | Data | Use |
|---|---|---|
| `message` | `{conversationId, message}` | Chat (your own other devices get `mine: true`) |
| `room` | `{planId, message}` | Plan Rooms |
| `notice` | notice | In-app notices |
| `date-location` | partner position | Date Mode, while both share |
| `pulse-status`, `pulse-status-cleared` | friend status, `{connectionId}` | Friends strip |
| `e2ee` | `{conversationId, messageId}` | Pull this device's inbox (§8) |
| `call`, `call-signal` | call view; `{callId, kind, payload}` | Calls (§9) |
| `live` | live view | A friend went live, or a live ended |
| `thread` | `{threadId, postId}` | New post in a Collaborative Thread |

## 6. Device attestation

Signup, phone sign-in and location updates may require `X-Device-Integrity` (servers run `off` / `monitor` / `enforce`).

1. Get a single-use challenge: `POST /attestation/challenges` (no auth needed before signup).
2. Attest:
   - **Android:** request a Play Integrity token with `requestHash = base64url(SHA-256(challenge + "|" + action))`. Send `play.<challenge>.<token>`.
   - **iOS:** once per install, `DCAppAttestService.generateKey` and `attestKey(keyId, SHA-256(challenge))`, then `POST /attestation/apple/keys`. Per request, `generateAssertion(keyId, SHA-256(challenge + "|" + action))` and send `appattest.<keyId>.<challenge>.<base64url(assertion)>`.

`action` is `signup`, `signin` or `location`. A token works once, for one action.

## 7. Media, location, consent

- **Media:**
  1. `POST /media/uploads` gives a signed URL.
  2. PUT the file to it.
  3. `POST /media/uploads/complete`.
  4. Poll `GET /media/uploads/status` until `READY`.
  5. Attach the `mediaRef` (moments, capsules, thread posts). Uploads stay attachable for 24 h.
  
  Media URLs are short-lived signed links, so don't cache them.
- **Location:** `PUT /location {lat, lon}` while in the foreground only. The server snaps it to a ~0.7 km² cell; raw coordinates are never stored. Updates are rate-limited, so don't send more than about once a minute.
- **Consent:** the first location share, Dating Lens, home region or wellbeing answer records consent. Show Privacy settings from `GET /consents`, where every purpose has its notice text and what withdrawing does.

## 8. End-to-end encrypted chat

The server is a key directory and relay, and the app runs the Signal protocol (X3DH plus the double ratchet), e.g. Signal's `libsignal` through platform channels or FFI.

1. **Register this install:** `POST /e2ee/devices` with the identity key, signed prekey and 100 one-time prekeys. Keep `deviceId`.
2. **Top up prekeys** when `oneTimePreKeysLeft` < 20: `POST /e2ee/devices/{id}/prekeys`.
3. **To send:**
   1. Fetch bundles: `GET /e2ee/connections/{connectionId}/bundles` (theirs) and `GET /e2ee/devices/{id}/bundles` (your other devices).
   2. Encrypt once per device.
   3. Compute `commitment = HMAC-SHA256(k, plaintext)` with a fresh random `k`. Put `k` **inside** the plaintext payload.
   4. `POST /conversations/{id}/encrypted`.
   
   On `409 DEVICE_LIST_MISMATCH`, refresh bundles for the devices in `missing` and `stale`, then resend.
4. **To receive:** on an `e2ee` socket event (or app start), `GET /e2ee/devices/{id}/inbox`, decrypt, store locally, then `POST …/inbox/ack`. Acked envelopes are deleted from the server.
5. **Empathy Mirror runs on the device:** download `GET /empathy/lexicon` (send `If-None-Match` with the ETag) and port the normalisation in `LexiconToneClassifier`.
6. **Reporting:** `POST /conversations/{id}/messages/{messageId}/report` with the plaintext and `k`. The server checks them against the commitment.
7. **Signing out unlinks the device.** On the next sign-in, register a new device.

## 9. Calls and Circle Live

- **Calls** (`flutter_webrtc`):
  1. `GET /calls/ice-servers` for STUN/TURN credentials (1 h).
  2. `POST /connections/{id}/calls`.
  3. Exchange SDP and ICE with `POST /calls/{id}/signal`; the other side receives `call-signal`.
- **Layers:** `PUT /calls/{id}/layer {wants}`. Render what `layer` says, and gate your own outgoing tracks on it:
  - `VOICE`: no video track;
  - `BLURRED`: blur before encoding;
  - `CLEAR`: normal video.
- **Circle Live** (`livekit_client`):
  - `POST /live` returns `access.serverUrl` and `access.token` for the host.
  - Viewers call `POST /live/{id}/join`, and call it again before `expiresAt` (10 min) to keep watching.

## 10. Push notifications

- **Register:** `POST /devices {pushToken, platform}` after sign-in; `POST /devices/unregister` on sign-out.
- **Payload:** `data.type` is one of `call`, `capsule`, `date`, … with the relevant id.
- **Discretion Mode:** the server already rewrites text neutrally, so show it as-is.

## 11. Screens and their endpoints

| Area | Main endpoints |
|---|---|
| Onboarding | `/auth/*`, `/verification/liveness`, `PATCH /profile/me`, `/consents` |
| Camera and stories | `/media/uploads*`, `POST /moments`, `/moments/{id}`, `/moments/{id}/relay`, `/moments/{id}/keep`, `/moments/trail` |
| Discovery and map | `/discover/constellation`, `/discover/heat`, `/map/stories`, `/prompts/today`, `/pulse` |
| Signals and connections | `/signals*`, `/connections*`, `/connections/{id}/spark`, `/couple` |
| Chat | `/conversations/{id}/messages`, `/encrypted`, `/balance`, `/e2ee/*` |
| Friends layer | `/pulse-status*`, `/threads*`, `/capsules*`, `/live*`, `/calls*` |
| Public | `/figures*`, `/amas*`, `/moments/{id}/spotlights`, `/spotlights/*` |
| Meet-ups | `/plans*`, `/right-now*`, `/dates*` (Date Mode), `/cities/current` |
| Safety and privacy | `/safety/blocks`, `/safety/reports` (one target field), `/grievances`, `/privacy/export`, `DELETE /privacy/account?confirm=DELETE` |
| Plus | `GET /plus`, `/plus/subscribe` (Razorpay checkout), `/plus/cancel` |

Use `GET /cities/current` to decide which city-gated features to show.
