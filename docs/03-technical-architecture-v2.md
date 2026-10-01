# 03 · Technical Architecture v2

> This document enhances the *Technical Architecture & System Design Specification*. The service names and entity vocabulary in that document (§3.1, §5) are **kept**. This document changes *how* v1 is built and deployed, specifies location privacy precisely, and adds the India compliance engineering. It describes the code that is in this repository (`src/main/java/oneday/...`).

---

## 0. Changes vs. v1 architecture

| # | v1 statement | v2 decision | Reason |
|---|---|---|---|
| A1 | 17 services, Cassandra + Kafka + OpenSearch + ClickHouse + K8s from the start | **Modular monolith** (one Spring Boot app, one package per service) on **MySQL 8**. Other stores are added at explicit **extraction triggers** (§1.3). | v1 is a single-city pilot. The v1 doc's own lessons from Instagram and Snap say *don't start there by default*, and §6.3 says *don't pre-provision speculatively*. |
| A2 | PostgreSQL+PostGIS *or* MySQL | **MySQL 8** (the existing `application.properties` and `mysql-connector-j`). Geospatial logic runs **in the application** using geohash cells, so it does not depend on a DB extension. | Removes a contradiction. Keeps the database portable (H2 for tests, MySQL in prod, Cassandra partitioning later). |
| A3 | "Randomized offset within band" | **Cell-snapped distance + stable per-pair jitter + probe budgets** (§5) | Noise that changes on every read averages out. 2024 oracle-trilateration research located users to 2–111 m. |
| A4 | Geohash "~600 m × 600 m" | Geohash **precision 6 ≈ 1.2 km × 0.6 km** (~0.73 km² at Bengaluru's latitude). Precision 5 (≈ 4.9 km) for heat aggregation. | The v1 figure was inaccurate. Precision 6 is a sound privacy floor. |
| A5 | Gateway-level verification gate | In the monolith: **Spring Security filter-chain rule on a `verified` JWT scope**, plus a **service-level re-check** against the DB. Moves to the API Gateway when services are extracted. | Same guarantee (no direct call can bypass it), with defence in depth for revoked verification. |
| A6 | Java 25 / Boot 4.1 | Kept (`java.version=25`). The code uses only Java 21-level language features, so CI can build it on 21 or 25. | The container used to build this milestone had only JDK 21. |
| A7 | India compliance = IT Rules + data residency | Adds **DPDP Act + Rules 2025**, the **2026 SGI amendment** and **POCSO** reporting (§8) | These regulations were notified after, or were missing from, the v1 doc. |

---

## 1. Architecture strategy

### 1.1 Modular monolith, with service boundaries kept

Each package maps 1:1 to a service in the v1 catalog. Packages talk to each other through service classes, never through each other's repositories. That makes extraction a transport change, not a redesign.

| Package (`oneday.*`) | v1 service (catalog §3.1) | M1 responsibility |
|---|---|---|
| `identity` | Identity & Authentication | Register (DOB, 18+ refusal, consent record), login, JWT issue |
| `verification` | Verification Service | Liveness + age-estimate cross-check via a `LivenessVerifier` port; manual-review routing |
| `profile` | User/Profile | Display name, activities, **values**, **languages**, **home region**, privacy, **Dating Lens**, discretion, radius |
| `geo` | *(part of Discovery & Ranking)* | Geohash, cell snapping, bands, direction, jitter, **probe budget**, velocity checks, **Safe Zones** |
| `moments` | Moment/Content | Stories with share scope, live-capture flag, 24 h expiry, Layer-0 vs Layer-2 views |
| `discovery` | Discovery & Ranking | Constellation (radius/city/roots/language), **Heat** (k-anon), narrative explanations, bounded sessions |
| `signals` | Reaction & Engagement | Signals, **Signal Budget**, 48 h Reaction Window, digest, reveal, pass, consent trail, expiry sweep |
| `connections` | Social Graph + Relationship State | Connections, soft exit, **Mutual Spark** |
| `chat` | Conversation/Messaging | Friend-Mode chat, Investment Balance |
| `safety` | Trust & Safety | Block (propagating), reports with priority routing |
| `pulse` | Notification (digest side) | Local Pulse: capped counts, k-anon nearby activity, discretion copy |
| `privacy` | *(new, DPDP)* | Self-service export and erasure |
| `events` | *(new)* | Transactional outbox, relay, idempotent consumers, dead-letter queue (§1.4) |
| `dates` | *(new, v1.5)* | Date Mode: plans, time-boxed exact location, trusted contact, check-ins, SOS, Mutual Debrief, Meeting Points |
| `ledger` | *(new)* | Real Value Ledger, a private read model built from events |

### 1.2 Runtime (v1)

```
Mobile app ──HTTPS──► Load balancer ──► OneDay (Spring Boot, N stateless replicas)
                                              │
                         ┌────────────────────┼─────────────────────────┐
                       MySQL 8            Redis (M2)             S3-compatible + CDN (M2)
                  (primary + replica)   rate limits,           media (pre-signed upload)
                                        probe budgets
```

Rate limits and probe budgets sit behind two ports (`RateLimiter`, `ProbeBudget`). The default is in-memory, for a single node. The `redis` profile (`oneday.state.store=redis`) swaps in Redis implementations so that multiple replicas share one budget. Each check runs as one atomic Lua script. Probe-budget cells are stored as keyed HMACs with a 1 h TTL, so Redis never holds a readable location trail. Media goes straight from the client to S3-compatible storage through pre-signed URLs (§3). It lands under `incoming/` and is **never served from there**. A media worker downloads it, strips all metadata (photos are re-encoded in-process; videos go through ffmpeg), writes the served object under `moments/`, and deletes the original. The worker runs on a small pool inside the app. Once video volume grows it should become its own deployment, because it needs ffmpeg.

One container image (`Dockerfile`: JRE 25 + ffmpeg, non-root) runs everything. `compose.yaml` is a production-shaped local stack: MySQL 8.4, Redis, and SeaweedFS as the S3-compatible store. With a non-AWS store the S3 client sends checksums only where an API requires them, because streaming checksums are handled unevenly outside AWS. Set `MANAGEMENT_SERVER_PORT` (e.g. 8081) on an internal-only port for probes and Prometheus.

### 1.3 Extraction triggers (when to add the v1-doc components)

| Component | Add when | First candidate to move |
|---|---|---|
| **Redis** | Before running more than one replica (M2) | Rate limits, probe budgets, sessions, presence |
| **Kafka** | ≥ 3 consumers need the same domain events, or ML features must be consumed asynchronously | `SignalCreated`, `MutualReveal`, `MomentPublished` (outbox table → Kafka) |
| **Cassandra** | Moments + signals + messages exceed ~5k writes/s sustained or ~1 TB hot, or MySQL p99 write latency passes 50 ms | `moments_by_geohash`, `signals_by_recipient`, `messages_by_conversation` (v1 doc §5.2) |
| **OpenSearch** | Discovery p95 > 150 ms at pilot density, or text search is needed | `moments_active` geo index (v1 doc §5.3) |
| **ClickHouse** | Analytics queries start to affect the OLTP database | Real Connection Index, experiments |
| **Kubernetes** | More than one deployable service exists | — |

### 1.4 Domain events: transactional outbox

Modules publish facts (`MomentPublished`, `SignalSent`, `MutualRevealed`, `MutualSparked`, `CoupleFormed`, `UserBlocked`, `DateConfirmed`, `DateCompleted`, `DateSafetyEscalated`; the catalogue is one sealed interface, `DomainEvent`) through `EventPublisher`, which writes to `outbox_events` **in the caller's transaction** (`Propagation.MANDATORY`). An event therefore exists if and only if its state change committed: there is no dual write.

- **Relay:** every replica runs `OutboxRelay`. It claims due events with a conditional `UPDATE` lease (portable to H2 and MySQL, no `SKIP LOCKED`), delivers them, then marks them `DISPATCHED`. If a relay dies, its lease simply runs out.
- **Consumers:** they are idempotent. `InProcessEventTransport` runs each `DomainEventHandler` in its own transaction together with an inbox row (`processed_events`, primary key `(handler, event_id)`). A retry after a partial failure re-runs only the consumers that failed.
- **Failures:** they back off exponentially. After `max-attempts` the event is parked as `DEAD`; admins list and requeue it at `/staff/events`. Gauges: `oneday_outbox_pending`, `oneday_outbox_dead`, `oneday_outbox_lag_seconds`.
- **Kafka later:** it replaces `EventTransport`, and neither the publishers nor the outbox change. Delivery is at least once and not ordered across aggregates, and `aggregateId` is the future partition key.
- **Current consumers:**
  - the Real Value Ledger projection;
  - the trusted-contact alert on `DateSafetyEscalated`, so the text is sent only if the escalation committed, and is retried if sending fails;
  - cancelling open plans on `UserBlocked`.
- **Erasure:** removes every event that mentions the account (`user_ids`).

---

## 2. Stack (v1)

| Concern | Choice |
|---|---|
| Language / framework | Java 25 (source level compatible with 21), Spring Boot 4.1.1, Spring Security 7 (OAuth2 resource server with HS256 JWT) |
| Persistence | Spring Data JPA (Hibernate 7), **Flyway** migrations (`db/migration`), MySQL 8 (`flyway-mysql`) |
| API docs | springdoc-openapi 3.1 (`/swagger-ui.html`, `/v3/api-docs`) |
| Tests | JUnit 5, MockMvc, **H2 in MySQL mode**, run with the same Flyway migrations as prod. Add Testcontainers MySQL in CI where Docker is available. |
| IDs | **UUIDv7** strings (time-ordered, so they index well in InnoDB and can't be enumerated) |
| Time | An injected `java.time.Clock` (UTC). Tests control time to exercise the 48 h window and 24 h expiry. |

---

## 3. API surface (Milestones 1–2)

All endpoints except sign-up, sign-in, `/auth/refresh`, OTP, health, API docs and `GET /grievances/officer` require a bearer JWT. Endpoints marked **V** also require the `verified` scope: these are the *contact-reaching* actions from the progressive-verification model (v1 §4.3, blueprint §41.4).

| Method & path | V | Purpose |
|---|---|---|
| `POST /auth/register` | | Email, password, DOB, consent version. **Under 18 → 422, nothing stored.** Rate-limited per IP. |
| `POST /auth/login` | | Starts a session: a 15-minute access token and a refresh token. Limited per network and per account. A suspended account gets a restricted session (`accountStatus: SUSPENDED`, no contact scopes). |
| `POST /auth/refresh` | | Swaps the refresh token for a new pair and recomputes scopes. Replaying a rotated token ends the session. |
| `POST /auth/logout` · `POST /auth/logout-all` · `GET /auth/sessions` · `DELETE /auth/sessions/{id}` | | Sign out this device or all of them, and list or sign out signed-in devices |
| `POST /auth/otp/request` · `POST /auth/otp/verify` | | Phone login. The request step responds the same way whether or not the number is registered. Verify logs in, or signs up a new number (same 18+ and consent checks). Codes are keyed-HMAC, expire in 5 min and lock after 5 attempts. |
| `POST /verification/liveness` | | Submits a liveness session token. Returns the verification result plus a **fresh token** with the `verified` scope. |
| `GET/PATCH /profile/me` | | Own profile (activities, values, languages, home region, private gender / interested-in, privacy, Dating Lens, discretion, radius, pulse hour) |
| `GET /location` · `PUT /location` | | Foreground ping: raw lat/lon in, **only the snapped cell stored** |
| `POST /location/pause` | | Removes the stored cell. Your moments immediately leave discovery. |
| `PUT /location/safe-zone` / `DELETE` | | Marks the *current* cell's area as a Safe Zone |
| `POST /media/uploads` | **V** | Upload ticket: pre-signed PUT to `incoming/` (type and length are signed) plus a random `mediaRef` with no user id |
| `POST /media/uploads/complete` · `GET /media/uploads/status?mediaRef=` | | Hand the upload to the worker; poll `AWAITING_UPLOAD → PROCESSING → READY / REJECTED` (with a reason) |
| `POST /moments` | **V** | Publishes a live-captured moment (`FRIENDS_ONLY` / `PUBLIC_DISCOVERY`). A photo or video must reference the poster's **own** upload from the last 24 h, used once. |
| `GET /moments/{id}` | | Layer-0 view for strangers (optional preview URL only). Full view for the owner and Connections. Media comes back as **short-lived URLs**, never storage keys. |
| `GET /discover/constellation?scope=RADIUS\|CITY\|ROOTS\|LANGUAGE&activity=&page=` | | Bounded batch of Ambient nodes. Returns `caughtUp=true` at the end. |
| `GET /discover/heat` | | k-anonymous activity levels per ~5 km cell |
| `POST /signals` | **V** | Sends a Signal to a moment (reaction + optional activity reference, **no free text**) |
| `GET /signals/digest` | | Bounded batch of pending, in-window signals received |
| `GET /signals/sent` | | Consent Trail (own sent signals, **no seen/ignored status**) |
| `POST /signals/{id}/reveal` | **V** | Mutual Reveal: creates the Connection + Conversation |
| `POST /signals/{id}/pass` | | Silent archive |
| `GET /connections` | | Own connections. `mutualSpark` is shown only if both sparked. |
| `POST /connections/{id}/spark` / `DELETE` | **V** | Mutual Spark (requires your own Dating Lens) |
| `POST /connections/{id}/exit` | | Soft exit (no notification) |
| `GET /conversations/{id}/messages` · `POST` (**V**) | | Friend-Mode chat. Empathy Mirror: a message that may land badly gets 422 `EMPATHY_CHECK` (with `tone` and a reflection in `detail`). Resending with `"sendAnyway":true` (and a new `Idempotency-Key`) delivers it, and the recipient's view carries `concern` ("Does this bother you?" plus the matching report category). The same applies to Room messages. |
| `GET /conversations/{id}/balance` | | Qualitative Investment Balance for the viewer |
| `POST /safety/blocks` · `POST /safety/reports` | | Target by exactly one of `momentId`, `signalId`, `connectionId`, `planId`, `roomMessageId`, `rightNowId`, `dateId` or `pulseStatusId` (modules contribute `SafetyTargetResolver` beans). A target the caller can't see is 404. Internal user IDs are never exposed. |
| `GET /pulse` | | Local Pulse digest (counts capped at "9+") |
| `GET /privacy/export` · `DELETE /privacy/account` | | DPDP / GDPR access and erasure (also deletes media objects after commit). Under a safety hold, erasure is deferred without any visible difference (§8). |
| `POST /devices` · `POST /devices/unregister` | | Push-token registration (≤ 5 per account, tokens never echoed back) |
| `GET /notices` · `POST /notices/{id}/read` | | In-app notices: warnings, report outcomes, security events, grievance updates |
| `POST /grievances` · `GET /grievances` · `GET /grievances/officer` (public) | | Complaints and appeals to the Grievance Officer, suspended accounts included. The reference and deadline are the acknowledgement. |
| `POST /connections/{id}/couple` (**V**) · `DELETE` | | Couple Mode: private until both confirm (needs a Mutual Spark); then Discovery pauses for both |
| `POST /dates` (**V**) · `GET /dates` · `GET /dates/{id}` | | Date Mode plans between Connections (Meeting Point or named public place, time box 30 min–8 h) |
| `POST /dates/{id}/accept` (**V**) · `/decline` · `/cancel` · `/end` | | Confirm (invited person only), decline, cancel, and the "home safe" end-of-date confirmation |
| `PUT /dates/{id}/sharing` · `PUT /dates/{id}/location` | | Exact location: per-person consent; it flows only while both share and inside the time box |
| `PUT/DELETE /dates/{id}/trusted-contact` · `GET /date-share/{token}` (public) | | The trusted contact's private link, which follows only the sharer's side of the plan |
| `PUT /dates/{id}/check-in-time` · `POST /dates/{id}/check-in` · `POST /dates/{id}/sos` | | "Going OK?", "I need help" and one-tap SOS. Returns `112`. The other person is never told. |
| `POST/GET /dates/{id}/debrief` | | Mutual Debrief: only positive answers both gave are revealed; safety answers never are |
| `GET /meeting-points?category=&radiusKm=` | | Safety-Verified Meeting Points near the caller's cell |
| `GET /ledger?month=` | | The caller's private Real Value Ledger |
| `GET /map/stories?scope=&activity=&todaysPrompt=` | | Story Map: public stories in adaptive k-anonymous clusters (§5.4) with Roots/Language/activity/prompt lenses and relay threads |
| `GET /prompts/today` · `GET/POST /staff/prompts` (*moderator*) | | Today's Prompt (give-to-get unlock); staff schedule global or Roots prompts |
| `POST /moments` with `promptKey` / `replyToMomentId` · `GET /moments/{id}/relay` | | Answer the prompt; join a Story Relay (public, one link per person, at most 30) |
| `GET/POST/DELETE /staff/festivals` | *moderator* | Festival Seasons: dated windows (1–31 days) for one home region or everyone; they drive Today's Prompt and label Story Map clusters |
| `GET /ledger/week` | | Weekly Recap: last week (local Monday–Sunday) as one highlight, a few facts and a kind ending |
| `GET /wellbeing/check` · `POST /wellbeing/answer` | | The rare "was your time well spent?" guardrail question (about 1 in 50 person-days, at most once per 14 days) |
| `POST /plans` (**V**) · `GET /plans` · `GET /plans/mine` · `GET /plans/{id}` · `POST /plans/{id}/join` (**V**) · `/leave` | | Plans & Rooms at Safety-Verified Meeting Points; joining needs the host's approval |
| `GET /plans/{id}/requests` · `POST /plans/{id}/requests/{handle}/approve` (**V**) · `/decline` · `GET/POST /plans/{id}/room` | | Opaque request handles (no user ids); a silent decline; the members-only Room |
| `POST /connections/{id}/vouch` (**V**) · `DELETE` · `GET /vouches/mine` | | Trusted Vouch; strangers see a capped count only |
| `POST/DELETE /moments/{id}/keep` · `GET /moments/trail` | | Private Memory Trail (area-level location only) |
| `WS /ws` (STOMP) | | Real time, receive-only: CONNECT with `Authorization: Bearer`; subscribe to `/user/queue/events` only. Events: `message`, `room`, `notice`, `date-location`, `pulse-status`, `pulse-status-cleared`. |
| `GET /plus` · `POST /plus/subscribe` · `POST /plus/cancel` · `POST /webhooks/razorpay` (public, HMAC-signed) | | OneDay Plus (convenience only); the webhook is idempotent by event id |
| `PUT /pulse-status` · `GET /pulse-status/mine` · `DELETE /pulse-status` · `GET /pulse-status/friends` | | Pulse Status: mood, 1–3 emoji, note ≤ 60, optional Spotify track (stored as the track id, shown via the official embed). Friends-only, 24 h, 12 updates/hour. |
| `GET /consents` · `POST /consents/{purpose}` · `DELETE /consents/{purpose}` · `GET /consents/history` | | DPDP consent per purpose (`LOCATION_DISCOVERY`, `DATING_PREFERENCES`, `ROOTS_AND_LANGUAGES`, `WELLBEING_SURVEY`), each with its notice and withdrawal effect. Withdrawal deletes the data in the same transaction; using it again gives 409 `CONSENT_WITHDRAWN` until it is granted again. |
| `POST /attestation/challenges` · `POST /attestation/apple/keys` | *public*, per-IP limits | Single-use 5-minute challenges, and iOS App Attest key registration (Apple's attestation verified once per install). Protected requests then send `X-Device-Integrity: appattest.<keyId>.<challenge>.<assertion>`, where the assertion is over `SHA256(challenge + "\|" + action)`. |
| any authenticated `POST` with `Idempotency-Key` | | The original response is replayed for a retry (`Idempotent-Replayed: true`); a reused key with a different body gets 422 |
| `GET /staff/metrics/engagement?weeks=` | *admin* | Weekly Meaningful Actives and the well-spent share, per ISO week |
| `POST /right-now` (**V**) · `GET /right-now` · `GET /right-now/mine` · `DELETE /right-now` | | Right Now (behind `oneday.right-now.enabled`, Gate 2): an activity for 30–120 min, shown at band precision |
| `POST /right-now/{id}/join` (**V**) · `GET /right-now/requests` · `POST /right-now/requests/{id}/accept` (**V**) · `/decline` | | "I'm up for it too" (5/day, no free text); accepting creates the Connection, declining is silent |
| `GET /staff/date-alerts` · `POST /staff/date-alerts/{dateId}/{userId}/resolve` · `POST/DELETE /staff/meeting-points` | *moderator* | Date Mode alert desk (reads audited) and Meeting Point curation |
| `GET /staff/events/dead` · `POST /staff/events/{id}/requeue` | *admin* | Outbox dead-letter queue |
| `GET /staff/verification-queue` · `POST /staff/verification/{userId}/decision` | *moderator* | Manual review: `APPROVE`, `RETRY`, or `REJECT` (also suspends the account) |
| `GET /staff/reports` · `POST /staff/reports/{id}/claim` · `POST /staff/reports/{id}/resolve` | *moderator* | Queue ordered by priority then age, with SLA `dueAt` and `overdue`. Resolve with `DISMISS`, `WARN` or `SUSPEND_USER`. The warned user and the reporter get notices. |
| `GET /staff/grievances` · `POST /staff/grievances/{id}/answer` | *moderator* | Grievances by deadline with overdue flags. The answer (`UPHELD` / `PARTLY_UPHELD` / `NOT_UPHELD` plus a written response) is audited and tells the complainant where to escalate. |
| `GET /actuator/prometheus` | *admin* | Metrics. Token-free only on the internal management port. |
| `GET /staff/erasures` | *moderator* | Erasures deferred by a safety hold, with the hold reason |
| `POST /staff/accounts/{userId}/reinstate` · `GET/PUT/DELETE /staff/members/…` · `GET /staff/audit` | *admin* | Reinstate accounts, manage staff roles (never your own), read the append-only audit log |

**No internal user ID is ever returned for another person.** Strangers are addressed through the moment or signal they are acting on, which prevents enumeration and scraping.

---

## 4. Security model

- **JWT (HS256)** with the claims `sub` = user ID, `sid` = session ID and `scope` = `member`, `member verified`, plus `moderator` / `admin` for staff. Staff scopes are issued only to active, verified accounts, and every staff request re-checks the role in the database. The first admin is bootstrapped by **user id** (`ONEDAY_BOOTSTRAP_ADMIN_IDS`), not by email, because emails are not ownership-verified yet. Access tokens last 15 minutes. Set the secret through `ONEDAY_JWT_SECRET` (at least 32 bytes). The app fails fast if the secret is missing or too short.
- **Sessions:**
  - Every sign-in is a row in `sessions`, and every request checks that its token's session is still live (one primary-key lookup). Signing out, suspension and erasure therefore take effect on the next request.
  - Refresh tokens are `<sessionId>.<256-bit secret>`, stored as SHA-256 and rotated on every use.
  - Presenting a rotated secret means two parties hold the session, so it ends for both and the holder gets a `SECURITY` notice. The exception is within 30 s, when it counts as a retry after a lost response.
  - Sessions end after 30 idle days, after 180 days in any case, and beyond 10 devices (the least recently used goes first).
- **Gate:** `SecurityFilterChain` requires `SCOPE_verified` on every **V** route. Services *also* call `UserGuard.requireContactAllowed(userId)`, which re-reads account status and verification status. A token issued before a suspension or re-verification failure therefore cannot be used for contact actions.
- **Signup abuse (blueprint §47.6):** per-IP rate limit on `/auth/register`, plus device attestation on signup, phone sign-in and location updates (Play Integrity on Android, App Attest on iOS; `off → monitor → enforce`). App Attest assertions are bound to a single-use challenge and to the action, and the key's counter must only increase, so a replayed or cloned assertion fails.
- **Passwords:** delegating encoder (bcrypt default). Sign-in attempts are limited per network (100/h) and per account (10/h). **Phone OTP** (M2) is the primary login method in India. Phone-only accounts have no password and cannot use password login.
- **Error contract:** RFC 9457 `ProblemDetail` with a stable `code` property and the `requestId`. Unexpected errors return `INTERNAL_ERROR` without internals.

---

## 5. Location privacy engineering (the core of the "advanced map")

### 5.1 Threat model

| Attacker | Technique | Defence |
|---|---|---|
| Curious stranger | Reads the band repeatedly | Band computed between **cell centres**, so moving within a cell changes nothing |
| Averaging attacker | Many queries, averaging noisy distances | **No fresh noise per read.** Jitter is a deterministic function of (pair, day), so repeated reads return the same answer. |
| **Oracle trilateration** (KU Leuven 2024) | Spoofs their own position step by step and watches where the band flips | (a) The attacker's own position is also **snapped** to a cell, so the probe resolution is one cell. (b) A **probe budget** caps distinct cells per hour. (c) A **minimum interval** between location updates. (d) A **velocity check** rejects teleports. (e) Constellation **query rate limit**. The best case for the attacker is learning the target's **cell** (≈0.73 km²), which is the designed privacy floor. |
| Routine tracker | Repeated sightings build a picture of home and work | **Safe Zones** collapse precision to "nearby area". Nodes expire after ~24 h. **No location history is stored.** |
| Database breach | Reads the location table | Only the *current* cell centre is stored (no raw coordinates, no history). Pausing deletes the row. |

### 5.2 Algorithm (implemented in `oneday.geo`)

1. **Ingest:** `PUT /location {lat, lon}` → validate range → `Geohash.encode(lat, lon, 6)` → store `(cell, cellCenterLat, cellCenterLon)`. The raw values are discarded immediately and never logged.
2. **Anti-spoof:**
   - reject updates sent sooner than `min-update-interval` (default 30 s);
   - reject speeds above `max-speed-kmh` (default 1000 km/h, which allows flights);
   - reject when the user has already visited more than `max-distinct-cells-per-hour` (default 12) distinct cells.

   A rejected update does not change the stored cell.
3. **Distance:** haversine between the **two cell centres**, never between raw points.
4. **Band with stable jitter:** band edges (1 km, 5 km, 15 km) are shifted by `j ∈ [−0.25, +0.25] km`. `j` is derived from `HMAC(secret, min(a,b) ‖ max(a,b) ‖ epochDay)`, so it is **symmetric for the pair, stable within a day, and different across pairs and days**.
5. **Direction:** 8-point compass bearing from centre to centre. When both are in the same cell the direction is `HERE`, which means no direction is given.
6. **Scopes:**
   - `RADIUS` → band + direction.
   - `CITY` / `ROOTS` → band is always `IN_CITY` and there is no direction (the Precision Ladder).
   - If the target is inside one of their **Safe Zones** (the geohash-5 prefix matches), the result is always `NEARBY_AREA` with no direction.
7. **Heat:** group live public moments by geohash-5 parent and activity. Emit a cell only when the number of **distinct owners ≥ k**, with a level of `LOW`/`ACTIVE`/`BUSY` (never exact counts).

### 5.4 Story Map clustering (k-anonymity for places)
A story carries its **capture cell** (geohash-6). The map never places an individual:
1. **Neighbourhood clusters** (radius lens only): a cell is emitted only if **≥ `neighbourhood-k` (3) distinct people** posted there.
2. **Area clusters:** the remaining stories roll up to their geohash-5 parent, which is emitted only with **≥ `area-k` (2) distinct people**.
3. **Unplaced shelf:** anything else, plus every story captured inside its owner's Safe Zone, goes to "around your city" with no coordinates.
4. City, Roots and Language lenses start at step 2, so wider reach means coarser placement.

Cluster coordinates are cell or area *centres*. Counts are capped at "9+". The viewer's own stories, and those of blocked people, are left out *before* clustering, so a block cannot be used to isolate someone. People who paused location, are unverified or are in Couple Mode are left out too.

### 5.3 Residual risk (stated honestly)
Someone who is physically present, or who probes slowly across days, can learn which ~0.73 km² cell a target is in while that target is sharing location. This is the product's accepted floor, and it is the same order of precision as "neighbourhood". Users who need more protection have **Safe Zones** and **pause**. M2 adds device attestation, which raises the cost of GPS spoofing further.

---

## 6. Data model (Flyway `V1`–`V27`)

| Table | Key columns | Notes |
|---|---|---|
| `users` | `id` PK, `email` UQ, `password_hash`, `date_of_birth`, `account_status`, `verification_status`, `verified_at`, `consent_version`, `consented_at` | v1 doc `User` + DPDP consent record |
| `verification_attempts` | `id`, `user_id`, `provider`, `outcome`, `estimated_age`, `confidence` | v1 doc `TrustRecord` |
| `profiles` | `user_id` PK, `display_name`, `bio`, `activities`, `core_values`, `languages`, `home_region`, `account_privacy`, `dating_lens`, `discretion_mode`, `discovery_radius_km`, `pulse_hour` | Small sets stored as delimited strings in M1. Normalise when they are needed in SQL predicates. |
| `user_locations` | `user_id` PK, `cell`, `cell_lat`, `cell_lon`, `safe_zone_prefix`, `updated_at` | **Current cell only.** Index on (`cell_lat`, `cell_lon`). |
| `moments` | `id`, `owner_id`, `kind`, `caption`, `activity_tag`, `media_ref`, `preview_allowed`, `share_scope`, `captured_live`, `cell`, `cell_lat`, `cell_lon`, `created_at`, `expires_at` | Later maps to `moments_by_geohash` / `moments_by_user` |
| `signals` | `id`, `sender_id`, `recipient_id`, `moment_id`, `reaction`, `activity_ref`, `status`, `created_at`, `window_expires_at`, `resolved_at` | UQ(`sender_id`,`moment_id`). Later maps to `interaction_events` + `reaction_window_expires_at` (blueprint §37.6). |
| `connections` | `id`, `user_a` < `user_b` UQ, `origin`, `state`, `spark_a`, `spark_b`, `ended_at` | Spark flags are private per side |
| `conversations` / `messages` | `conversation.connection_id` UQ; `messages(conversation_id, created_at)` | 1:1 in M1. Plans can add group conversations later. |
| `blocks` | PK(`blocker_id`, `blocked_id`) | Checked in both directions everywhere |
| `reports` | `id`, `reporter_id`, `reported_id`, `category`, `priority`, `status`, `details`, `assignee_id`, `resolution`, `resolved_by`, `resolved_at` | Kept after erasure where the law requires it (reporter reference nulled) |
| `staff_members` / `staff_actions` (V2) | role per user; append-only audit rows | Audit rows are kept after erasure of the subject |
| `media_uploads` (V3) | `owner_id`, random `object_key`, `kind`, `content_type`, `size_bytes` | The only link between a media object and an account |
| `media_uploads` processing columns (V5) | `incoming_key`, `status`, `reject_reason`, `attempts`, `updated_at` | Rows from before processing existed default to `REJECTED`, so they can never be attached |
| `users` hold columns (V6) | `erasure_requested_at`, `held_identifiers` | Set only while a deferred erasure waits for a hold to lift |
| `devices` · `pulse_deliveries` · `notices` (V7), `profiles.time_zone` | push tokens; one claim row per (user, local date); notices | The claim row's primary key makes the daily pulse idempotent across replicas |
| `sessions` (V8) | `user_id`, `refresh_hash`, `previous_hash`, `rotated_at`, `device`, `last_used_at`, `expires_at`, `ended_at`, `end_reason` | Hashes only, never a usable secret. Ended rows are purged after 30 days. |
| `grievances` (V9) | `reference` UQ, `user_id`, `category`, `description`, `status`, `resolve_by`, `outcome`, `response`, `resolved_by` | `resolve_by` is set from the category's legal deadline when the grievance is filed |
| `outbox_events` · `processed_events` (V10) | `event_type`, `schema_version`, `aggregate_id`, `user_ids`, `payload`, `status`, `attempts`, `next_attempt_at`, `locked_by`, `locked_until` · PK(`handler`, `event_id`) | Outbox and inbox. Delivered rows and inbox rows are purged after 7 days. |
| `connections` couple columns (V11) | `couple_a`, `couple_b`, `couple_since` | Private per side, like spark flags |
| `ledger_entries` (V12) | `user_id`, `kind`, `occurred_at`, UQ(`user_id`, `kind`, `source_event_id`) | Read model; the unique key keeps the projection exactly-once |
| `meeting_points` · `date_plans` · `date_participants` (V13) | venue + coordinates · plan state machine with `row_version` · per-person consent, current point, check-in, escalation, trusted contact, token hash, debrief | Positions, contacts and tokens are purged 2 h after a plan closes (an open escalation keeps them up to 7 days) |
| `daily_prompts` (V14); `moments.prompt_key`, `relay_root_id`, `reply_to_id`, `relay_depth`; `profiles.country_code` | prompt date + optional home region · relay links · ISO country | Prompts per local date; the country picks the emergency number |
| `users.reverify_reminded_at` (V15) | status `EXPIRED` | Liveness re-check every 90 days; one reminder 7 days before |
| `weekly_actives` · `wellbeing_answers` (V16) | PK(`user_id`, `week_start`) · `well_spent` | North-star projection from events (two-way conversations are counted from `messages`) and the guardrail answers |
| `festival_seasons` (V17) | `name`, `home_region`, `starts_on`, `ends_on`, `prompt_text` | Entered per year (lunar dates move); prompt keys `f:<id>:<yyyymmdd>` |
| `right_now_sessions` · `right_now_joins` (V18) | activity + time box · UQ(`session_id`, `joiner_id`), status | Feature-flagged until Gate 2 |
| `idempotency_keys` (V19), `profiles(time_zone, pulse_hour)` index | PK(`user_id`, `idem_key`), request hash, stored response | Kept 24 h; erased with the account |
| `plans` · `plan_members` · `plan_messages` (V20) | venue position, capacity, Roots region, `row_version` · member status · Room messages | Plans and Rooms are deleted a day after the plan ends |
| `vouches` (V21) | PK(`voucher_id`, `vouchee_id`) | Removed on block or erasure |
| `moments.kept`, `moments.trail_media_ref` (V22) | Memory Trail | Kept stories survive retention with their area only |
| `subscriptions` · `payment_webhook_events` (V23) | provider subscription id UQ, status, `current_end`, `cancel_at_cycle_end` · applied webhook ids | Kept on erasure with `user_id` replaced (tax law) |
| `consent_records` (V24) | `user_id`, `purpose`, `action` (`GRANTED`/`WITHDRAWN`), `notice_version`, `source` (`APP_ACTION`/`SETTINGS`); index (`user_id`, `purpose`, `created_at`) | Append-only; the latest row is the current state. Pseudonymised on erasure: proof of consent stays with the fiduciary (DPDP s.6(10)). |
| `messages.tone_flag` · `plan_messages.tone_flag` (V26) | Empathy Mirror tone of a message sent anyway, or NULL | Drives the recipient's "does this bother you?" |
| `attestation_challenges` · `app_attest_keys` (V27) | challenge PK + `expires_at` · `key_id` PK, `public_key`, `sign_count`, `environment` | Challenges are consumed with one conditional DELETE, so each is single-use across replicas. The counter advances with a conditional UPDATE. |
| `pulse_statuses` (V25) | PK `user_id`, `id` UQ (new on every update), `mood`, `emoji`, `note`, `spotify_track_id`, `expires_at` (indexed) | One per person; reads filter on expiry and a sweeper deletes expired rows |
| `otp_challenges` (V4) | `phone_hash`, `code_hash`, `attempts`, `expires_at`, `consumed_at` | Keyed HMACs only. Swept after a day. V4 also adds `users.phone` and makes email/password nullable. |

---

## 7. Signals & Reaction Window mechanics

```
PENDING ──reveal──► REVEALED  (Connection + Conversation created atomically)
   │ ├──pass─────► PASSED     (silent)
   │ └──block────► ARCHIVED
   └──now > window_expires_at──► ARCHIVED (sweep every 15 min; reads also filter by window)
```

- `window_expires_at = moment.created_at + 48 h` (configurable).
- **Budget:** `count(signals sent by user in the last 24 h) < daily-budget`. The count comes from the DB, so it holds across replicas.
- **Recipient protection:** the digest returns at most `digest-batch-size` signals per call and ranks them by shared context (roots, values, activity reference). Blocked pairs are excluded both ways.
- **Sender view:** the Consent Trail shows only *what I sent and when*, and whether it became a Connection. `PASSED`, `ARCHIVED` and "seen" are never distinguishable to the sender.

---

## 8. Compliance engineering (India first, region-configurable)

| Obligation | Engineering control | Milestone |
|---|---|---|
| DPDP: notice & consent | `consent_version` + `consented_at` recorded at signup. Notice text is versioned in the app. | M1 ✅ |
| DPDP s.6: consent per purpose, withdrawable with comparable ease, provable | Append-only consent ledger per purpose, with notice version and source. It is recorded at the affirmative action. Withdrawal runs each owning module's `ConsentWithdrawalEffect` in one transaction (data deleted, further use refused with 409). The history is in the export and is pseudonymised on erasure. | ✅ |
| DPDP: rights (access, erasure, correction, grievance) | `GET /privacy/export`, `DELETE /privacy/account`, `PUT /profile/me`, `POST /grievances` (category `PRIVACY`, answer routed to the Data Protection Board if not satisfied). Suspended accounts keep these rights through a restricted sign-in. | M1 ✅ / M2 ✅ |
| DPDP: children | **Refuse under-18 at signup** (nothing persisted). Liveness age-estimate cross-check. `UNDERAGE_SUSPECTED` reports go to P0. | M1 ✅ |
| DPDP: breach notice within 72 h | Runbook, audit log, on-call rota, contact template for the Data Protection Board | M2 |
| DPDP: Consent Managers (registration opens 13 Nov 2026) | A consent API that accepts and honours consent artefacts | M3 |
| IT Rules: grievance officer, takedown SLAs (some 2–3 h) | Staff console: priority queue with due times (P0 2 h) and overdue flags, suspension, append-only audit trail ✅. Grievances (rule 3(2)): published officer contact, acknowledgement on filing, 24 h for intimate imagery, 72 h for content removal and 15 days otherwise, appeals from suspended accounts, and escalation to the Grievance Appellate Committee ✅. On-call rota still to come. | M2 ✅ |
| Photo metadata (location in EXIF, MP4 location atoms) | The media worker re-encodes every upload without metadata before anything is served. Tests plant a GPS-like secret and assert it is gone. | M2 ✅ |
| Date Mode exact location (the most sensitive data in the product) | Mutual consent per plan, inside the time box only, one overwritten point per person, stopped at plan close, purged after the after-care window. Never logged. A trusted contact sees only the sharer's side, through a hashed, expiring token. | M4 ✅ |
| Story retention (no location history) | Expired story rows, with their capture cell, are deleted 14 days after expiry (`oneday.moments.retention-after-expiry`). Accounts under a safety hold keep theirs as evidence. | M2 ✅ |
| Evidence retention vs. erasure (POCSO; IT Rules 2021 Rule 3(1)(g), 180 days) | Erasure is deferred while an open P0 report exists or within 180 days of an enforcement action. The account is hidden and its identifiers released at once, with no tip-off, and it is erased automatically when the hold lifts. | M2 ✅ |
| IT Rules 2026 SGI labelling | Live-capture-only Discovery Mode. Labelled lenses in Friend Mode. SGI declaration for any future upload path. | M1 (capture flag) / M2 (client attestation) |
| POCSO mandatory reporting | Evidence-preservation hold on P0 reports, counsel-designed reporting SOP | Before public launch |
| Data residency | Host in Indian cloud regions (e.g. Mumbai / Hyderabad) for Indian users | M2 infra |

---

## 9. Observability
- **Request ids:** every request gets an `X-Request-Id` (the client's own if it is well formed). It appears on every log line of the request, on every response and in every error body, so a user's report leads to the exact log lines.
- **Metrics:** Prometheus at `/actuator/prometheus`, token-free only on the internal management port. Besides JVM, HTTP and pool metrics:
  - core-loop counters: `oneday_moments_published`, `oneday_signals_sent`, `oneday_reveals`;
  - safety counters: `oneday_reports_filed{priority}`, `oneday_auth_logins{outcome}`, `oneday_sessions_reuse_detected`, `oneday_media_processed{outcome}`, `oneday_grievances_filed{category}`;
  - gauges to alert on: `oneday_reports_overdue{priority}` and `oneday_grievances_overdue`;
  - behavioural safety: `oneday_pacing{action}`, and `oneday_empathy_mirror{surface,outcome}` (the share of prompted messages that are reconsidered is `1 − sent_anyway / prompted`).

  Tags are fixed vocabularies, never user ids or places.
- **Audit:** staff decisions go to the append-only `staff_actions` log.
- **Never logged:** raw coordinates and message bodies.
- **Later:** OpenTelemetry traces once there is more than one service.

## 10. Test strategy
- **Unit:** `Geohash`, `LocationPrivacy` (determinism, symmetry, band edges, safe-zone collapse), UUIDv7 ordering.
- **Integration (MockMvc + H2 + Flyway):** the full core loop runs end to end: register → verification gate (403 before verifying) → location → moment → constellation → signal → digest → reveal → chat → spark (one-sided hidden, mutual shown) → block → export → erase. Also covers under-18 refusal, the Signal Budget, 48 h expiry with a controlled clock, and the anti-spoof velocity check.
- **CI (M2) ✅:**
  - The same suite runs on H2 and on MySQL 8.4 (a service container), with ffmpeg and redis-server installed. A skipped test fails the build.
  - The container image is built and the compose stack is started.
  - `scripts/smoke-test.sh` then drives the whole loop over HTTP: signed upload to the real S3 API, metadata stripped, reveal, refresh-token rotation, sign-out and erasure.
