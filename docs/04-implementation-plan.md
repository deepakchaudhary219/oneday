# 04 · Implementation Plan

> This plan turns `01-market-and-feasibility.md`, `02-product-blueprint-v2.md` and `03-technical-architecture-v2.md` into milestones with exit criteria. Durations are **estimates** for a small team. They assume the team in §2 and should be re-estimated after M2.

---

## 1. Guiding rules

1. **Ship the core loop in one city before anything else.** The core loop is: live story → nearby discovery → signal → reveal → chat.
2. **Safety ships before the feature it protects.** The verification gate comes before contact. Date Mode comes before dating marketing. Takedown on-call comes before public launch.
3. **Density gates, not calendar dates, decide expansion** (§5).
4. **Build it in the modular monolith first.** Extract a service only when a trigger in Tech Arch v2 §1.3 fires.

## 2. Team (minimum viable)

| Role | Count | Notes |
|---|---|---|
| Founder / PM | 1 | Owns the density playbook and the decisions in blueprint v2 §13 |
| Backend (Java/Spring) | 2 | One of them owns location privacy and safety |
| Mobile (Flutter recommended; Android-first QA) | 2 | Camera, constellation UI, chat |
| Product designer | 1 | Visual system from blueprint §23 and §30 |
| Trust & Safety lead + contracted 24×7 moderators | 1 + vendor | Needed **before public launch** (IT Rules SLAs) |
| Legal counsel (part-time / retained) | — | DPDP, IT Rules, POCSO, liability (§47.3) |
| City lead + campus ambassadors | 1 + N | From the closed beta onwards |

## 3. Milestones

### M0 · Foundations & research (weeks 0–6, runs in parallel with M1–M2)
- 20–30 user interviews in the launch city: women, migrants, students, young professionals.
- Legal: DPDP notice and consent text, terms of service, grievance officer, POCSO SOP, insurance.
- Vendors: liveness and age-estimation (bias test on Indian skin tones and budget cameras), SMS OTP, maps/tiles (only needed for Meeting Points), cloud region in India.
- Design: visual system, Constellation motion study, microcopy in English, Hindi and Kannada.
- **Exit:** founder decisions in blueprint v2 §13 are signed off.

### M1 · Backend core loop ✅ *(started and delivered in this repository)*
The scope and status are listed in §6 below.
- **Exit:** the full core loop passes end-to-end tests. The verification gate is enforced. The location-privacy unit tests pass.

### M2 · Platform hardening (≈ 4–6 weeks) 🟡 *in progress; delivered items are listed in §7*
- ✅ Redis for rate limits and probe budgets, so horizontal replicas share limits.
- ✅ Media: pre-signed S3/MinIO upload with ownership checks and short-lived view URLs. ✅ Processing worker that strips metadata (photos re-encoded; videos via ffmpeg) and writes the Layer-0 preview. ⏳ CDN in front of the bucket.
- ✅ Manual-review console for `MANUAL_REVIEW`. ⏳ Liveness vendor adapter (replaces `DevLivenessVerifier`).
- ✅ Phone OTP login (SMS port). ⏳ DLT-registered SMS adapter. ⏳ Device attestation (Play Integrity / App Attest) on signup and location updates.
- ✅ Push delivery for the **Local Pulse** at the user's chosen hour and time zone, with Discretion Mode copy, plus in-app safety notices. ⏳ FCM/APNs adapter.
- ✅ Deferred erasure under a safety hold (open P0 report, or enforcement within the 180-day IT Rules retention period).
- ✅ Staff audit log and report-handling console with SLA timers. ✅ Grievance redressal (IT Rules 3(2), DPDP s.13), including appeals from suspended accounts. ⏳ Breach runbook.
- ✅ Sessions: 15-minute access tokens bound to a sign-in session, rotating refresh tokens with reuse detection, sign-out of one or all devices, and sessions ended on suspension and erasure. Password sign-in is rate limited.
- ✅ Observability: request ids on every response, log line and error; Prometheus metrics with product counters and overdue-work gauges. ⏳ Distributed tracing (OpenTelemetry) once there is more than one service.
- ✅ CI: GitHub Actions runs the suite on H2 and on MySQL 8.4, then builds the container image and smoke-tests the full compose stack. ⏳ Deploy to an Indian cloud region.
- **Exit:** load test at 5× the expected pilot peak. Staff can execute the takedown SLA.

### M3 · Mobile app v1 (≈ 8–12 weeks, starts once M1 APIs are frozen)
- Camera-first capture (live only in Discovery Mode). Share-scope picker.
- Constellation view (radius dial, scopes, activity grouping, "caught up" screen). Heat overlay.
- Digest (Local Pulse), Signal composer (4 reactions + activity reference), reveal and pass.
- Friend-Mode chat, Investment Balance, soft exit, block/report sheets.
- Preference Center (blueprint §32.1): radius, notification cadence, Dating Lens, discretion, Safe Zones, pause.
- Vernacular: English, Hindi and Kannada at pilot.
- **Exit:** internal dog-food with 50+ staff and friends for 2 weeks with no P0 safety bugs.

### M4 · Dating-ready v1.5 (≈ 4–6 weeks) 🟡 *backend in progress; delivered items are listed in §8*
- ✅ Date Mode: plan confirmation, **time-boxed exact location** (both consent, auto-expire), trusted contact share, scheduled check-in, **one-tap 112**, end-of-date confirmation.
- ✅ Safety-Verified Meeting Points (staff-curated venues). ⏳ Partner-venue programme, Discovery Trails.
- ✅ Mutual Debrief (reveals only overlapping positive answers). ✅ Couple Mode.
- ✅ Real Value Ledger (blueprint v2 §7.3), built from domain events.
- Incident-response protocol with local police liaison in the launch city (§47.2).
- **Exit:** tabletop exercise of a real-world harm incident is completed. Legal sign-off.

### M5 · Closed beta (≈ 6–8 weeks)
- Ambassador-seeded cohorts from campuses and workplaces in 2–3 pilot neighbourhoods. Anchor events that tie into Roots (for example a regional food meet-up).
- Gender-balance incentive for the under-represented side (blueprint §21.6).
- **Exit:** Density Gate 1 (§5).

### M6 · Public city pilot → v2
- Open signups citywide. Paid acquisition in the channels users already use. Referral via "invite a friend to an Activity" (§41.5).
- Start v2: Right Now (only after Gate 2), Plans and Rooms, Pulse Status + Spotify, Trusted Vouch, Pacing Guardian, Empathy Mirror, E2EE chat.

**Indicative total from start to public pilot: about 6–8 months** with the team above. Most of the risk is in M5 density, not in engineering.

## 4. Engineering backlog (ordered)

Done in M2 so far: Redis state store, staff console with manual review, media uploads with ownership checks, phone OTP, the media processing worker, deferred erasure under a safety hold, Local Pulse push notifications, CI with a container build, sessions with refresh tokens, grievance redressal, observability, and the outbox with domain events (see §7). Done in M4 so far: Date Mode, Meeting Points, Mutual Debrief, Couple Mode and the Real Value Ledger (see §8).

1. **Vendor adapters** behind the ports that already exist: liveness/age estimation (replaces `DevLivenessVerifier`), a TRAI DLT-registered SMS sender, and FCM/APNs push.
2. **Deployment:** an Indian cloud region, a separate media-worker deployment (same image, it needs ffmpeg), the management port on the internal network for Prometheus, and alerts on `oneday_reports_overdue`, `oneday_grievances_overdue` and `oneday_sessions_reuse_detected_total`.
3. Device attestation (Play Integrity / App Attest) on signup and location updates. Cache the per-request session check (one primary-key lookup today) if it shows up in load tests.
4. ✅ Outbox table + domain events. Next: move the remaining inline side effects (pushes, report-outcome notices) onto events, and add a Kafka `EventTransport` once a trigger in tech arch v2 §1.3 fires.
5. Local Pulse at scale: the job scans every user with a device every 10 minutes, which is fine for one city. Index by (time zone, pulse hour) before multi-city.
6. Pacing Guardian heuristics on the message stream (v2).
7. Normalised `profile_languages` / `profile_home_region` indexes when Roots scope needs SQL-side filtering.
8. Re-verification every 90 days (blueprint §21.4) as a scheduled job.

## 5. Density gates (proposed hypotheses; calibrate in M5)

| Gate | Condition (all of them, sustained for 4 weeks) | Unlocks |
|---|---|---|
| **Gate 1: beta → public pilot** | ≥ 2,000 weekly-active *verified* users inside pilot neighbourhoods · median Constellation batch at evening peak ≥ 8 nodes · signal→reveal rate ≥ 15% · P0 safety reports handled within SLA 100% · the smaller gender group ≥ 35% of weekly actives | Citywide signups, paid acquisition |
| **Gate 2: pilot → Right Now** | ≥ 10,000 weekly-active verified users citywide · ≥ 25% of new Connections exchange ≥ 10 messages · ≥ 5% of Connections create a plan or meet · referral share of signups ≥ 10% by month 3 | Right Now, Plans at scale |
| **Gate 3: second city** | Gate 2 holds for 8 weeks · CAC payback in line with retained value (blueprint §46.3) | Multi-city playbook (§28.3) |

## 6. Milestone 1: delivered in this repository

| Capability | Where | Status |
|---|---|---|
| Registration with DOB, **18+ refusal**, DPDP consent record, per-IP rate limit | `identity` | ✅ |
| JWT auth; **verified-scope gate** in the filter chain + service re-check | `security`, `identity/UserGuard` | ✅ |
| Liveness/age-estimate port with dev adapter, manual-review routing, fresh token on success | `verification` | ✅ |
| Profile: activities, **Values Compass**, languages, **home region**, privacy, **Dating Lens**, private gender and "interested in", discretion, radius | `profile` | ✅ |
| Location: cell-only storage, **velocity check, update interval, probe budget, Safe Zones**, pause | `geo` | ✅ |
| Moments with share scope, live-capture flag, preview permission, 24 h expiry, Layer-0 vs full view | `moments` | ✅ |
| Constellation: RADIUS / CITY / ROOTS / LANGUAGE scopes, activity filter, **stable-jitter bands**, direction, narrative explanations, bounded pages + "caught up" | `discovery` | ✅ |
| **Heat** layer with k-anonymity | `discovery` | ✅ |
| Signals: 4 reactions + activity reference, **Signal Budget**, **48 h Reaction Window**, digest, reveal, pass, Consent Trail, expiry sweep | `signals` | ✅ |
| Connections, soft exit, **Mutual Spark** (one-sided never revealed) | `connections` | ✅ |
| Friend-Mode chat + qualitative **Investment Balance** | `chat` | ✅ |
| Block (propagates to connections and signals, both directions) and reports with **P0 routing** | `safety` | ✅ |
| Local Pulse (capped counts, k-anon nearby activity, discretion copy) | `pulse` | ✅ |
| DPDP export + erasure | `privacy` | ✅ |
| Flyway schema, H2-backed integration tests, OpenAPI docs | `resources/db/migration`, `src/test` | ✅ |

**Verification (at the end of M1):** 34 automated tests pass (unit tests for geohash, location privacy and IDs, plus end-to-end API tests covering the full loop, the verification gate, under-18 refusal, the Signal Budget, 48 h expiry, anti-spoofing, Roots/Language scopes, Safe Zones, k-anonymous heat, bounded discovery, and export/erasure). The README's curl walkthrough was also run against a live server.

**Run locally:** see the repository `README.md`.

## 7. Milestone 2: delivered so far

| Capability | Where | Status |
|---|---|---|
| Rate limits and location probe budgets shared across replicas: atomic Lua scripts, keyed-HMAC cells so Redis never holds a readable trail (`redis` profile) | `common`, `geo` | ✅ |
| Trust & Safety console: staff roles in tokens with a database re-check, manual-review decisions (approve / retry / reject+suspend), report queue by priority with SLA due times and overdue flags, claim/resolve, suspend/reinstate, staff management, append-only audit log | `staff`, `identity`, `safety` | ✅ |
| Media uploads: pre-signed S3/MinIO PUT with signed type and length, random keys with no user id, moments may attach only the poster's own recent unused upload, short-lived view URLs, object deletion on moment delete and erasure | `media`, `moments` | ✅ |
| Phone OTP login: non-enumerating requests, keyed-HMAC storage, 5-minute expiry, attempt lockout, per-number and per-network limits, 18+ and consent checks for phone signup | `identity`, `sms` | ✅ |
| Media processing worker: uploads land in `incoming/`; photos are decoded with metadata ignored, checked for pixel bombs, orientation-corrected and re-encoded with no metadata; videos are transcoded by ffmpeg with `-map_metadata -1` plus a silent 4-second 360 px preview; only `READY` media can be attached; bad files are rejected with a reason; crash-recovery sweep. The dev object store is a real signed in-memory store. | `media` | ✅ |
| Deferred erasure under a safety hold: an erasure request from an account with an open P0 report, or one suspended within the last 180 days, looks exactly like a normal erasure to its holder, but records are kept until the hold lifts and then erased automatically. Staff see pending erasures. | `privacy`, `identity`, `safety`, `staff` | ✅ |
| Local Pulse push: device registration, per-user time zone, one push per local day at the chosen hour, only when there is something real to say, idempotent across replicas; Discretion Mode copy; safety-outcome notices to the warned user and the reporter (without revealing penalties) | `notify` | ✅ |
| CI and container: the suite on H2 and on MySQL 8.4 (fails if any test is skipped), a JRE 25 + ffmpeg image running as non-root, a `docker compose` stack (MySQL, Redis, SeaweedFS as the S3 store), and a smoke test of the whole loop against the stack | `.github`, `Dockerfile`, `compose.yaml`, `scripts` | ✅ |
| Sessions: access tokens bound to a session and checked on every request; refresh tokens stored as SHA-256, rotated on use, a replay ends the session (30 s grace for retries after a lost response); idle and absolute expiry; 10-device cap; sign out one device or all; suspension and erasure end sessions; suspended accounts get a restricted sign-in for export and appeal; per-network and per-account sign-in limits | `security`, `identity` | ✅ |
| Grievance redressal: file and track grievances (suspended accounts too); instant acknowledgement with reference and deadline; 24 h for intimate imagery, 72 h for content removal, 15 days otherwise; staff queue by deadline with overdue flags; answers audited and routed onwards (Grievance Appellate Committee or Data Protection Board); published officer contact | `grievance`, `staff` | ✅ |
| Observability: `X-Request-Id` on every response, log line and error body; unhandled errors logged in context and answered without internals; Prometheus metrics (token-free only on the internal management port) with core-loop and safety counters and overdue-work gauges | `common`, `staff` | ✅ |

| Transactional outbox: events stored in the same transaction as their state change (`MANDATORY` propagation), relayed at least once by every replica under per-event leases, idempotent consumers through an inbox table, exponential backoff, a dead-letter queue with staff requeue, lag/backlog/dead gauges, erasure of events that mention an account | `events`, `staff` | ✅ |

**Verification (round 5):** 98 automated tests pass on both H2 and MySQL 8.0, with only the two ffmpeg video tests skipped (ffmpeg was not installed in this environment). The Redis tests ran against a real `redis-server`. This round found and fixed:
- instants carried nanoseconds while `DATETIME(6)` stores microseconds, so a row written "now" could round up past "now" and stay invisible to `<= now` queries. The application clock now ticks in microseconds.

**Verification (round 4):** 91 automated tests pass, with nothing skipped, on both H2 and MySQL 8.4 (and on MySQL 8.0). CI runs both on JDK 25. The container image was built, and the compose stack passed the smoke test repeatedly. This round found and fixed:
- intermittent media-processing failures against a non-AWS S3 store: the SDK's default streaming checksums, caught by the CI smoke test;
- password sign-in had no rate limit;
- stateless tokens could not be revoked, so a stolen token outlived sign-out and suspension;
- suspended accounts could not sign in, so they could not exercise their data rights or appeal;
- unhandled errors were logged after the request context was gone;
- `mvnw` was committed without its executable bit.

**Verification (round 3):** 75 automated tests passed, with nothing skipped in this environment. They included:
- the Redis implementations, run against a real `redis-server`;
- video processing, run against real `ffmpeg`;
- a check that a GPS-like secret planted in photo EXIF/APP1/comment segments and in MP4 container tags is absent from the served files;
- an offline check that the real S3 presigner signs content type and length.

Where `redis-server` or `ffmpeg` is missing, those tests skip automatically. The new tests found and fixed seven bugs:
- a phone signup refused for being under 18 returned 500 instead of 422;
- phone login by a suspended account returned 500 instead of 403;
- data export crashed for accounts without an email;
- suspended accounts could not export their data;
- media processing run inline lost its status updates, because it joined an already-committed transaction;
- the daily-pulse claim upserted instead of inserting, so a second pass sent a duplicate;
- the device cap could evict the device being registered.

## 8. Milestone 4 (dating-ready v1.5): delivered so far

| Capability | Where | Status |
|---|---|---|
| Date Mode plans between active Connections: propose (a Meeting Point or a named public place, 30 min to 8 h, up to 30 days ahead, one open plan per Connection), accept/decline by the invited person, cancel by either, and expiry of unanswered proposals. The plan's state machine uses optimistic locking, so replicas can't race. | `dates` | ✅ |
| **Time-boxed exact location:** each person switches sharing on for one plan; positions flow only while *both* share, only from 30 min before the start until the end, one overwritten point per person (no trail), hidden once stale, and stopped for both the moment the plan closes. Blocks and soft exits end it at read time as well. | `dates` | ✅ |
| **Trusted contact:** a per-plan contact is texted a private link (256-bit token, stored as SHA-256) that follows only the sharer's side: place, times, live position, check-ins, alerts and "home safe". It needs no account and stops working after the plan's after-care window. | `dates`, `sms` | ✅ |
| **Check-ins and SOS:** "Going OK?" at a time each person chooses (default: 1 h in); "I need help", one-tap SOS or an unanswered prompt (with a trusted contact set) escalates through the outbox to a text to the contact and to the Trust & Safety alert desk (with the last shared position, and every read audited). The other person is never told. SOS keeps working after a mid-date block. | `dates`, `events`, `staff` | ✅ |
| **End of date and Mutual Debrief:** "home safe" confirmation; the debrief reveals only positive answers both people gave, and only once both have answered; missing safety answers privately offer the report route. | `dates` | ✅ |
| **Safety-Verified Meeting Points:** moderator-curated public venues (audited), listed nearest first from the viewer's own cell | `dates` | ✅ |
| **Couple Mode:** each side's confirmation is private; once both confirm (on top of a Mutual Spark), both people leave Discovery in both directions (not shown, no browsing, no new signals) while Friend Mode continues. Withdrawing a spark, exiting or blocking ends it. | `connections`, `discovery`, `signals` | ✅ |
| **Real Value Ledger:** a private monthly read model built from `MutualRevealed`, `MutualSparked`, `DateCompleted` and `CoupleFormed` events (including Roots connections), plus "conversations that went somewhere". No ranks or comparisons. Included in export and removed on erasure. | `ledger`, `chat` | ✅ |

Data retention: trusted-contact details, share links and positions are purged 2 h after a plan closes. The exception is an unresolved safety escalation, which keeps its evidence until staff resolve it, and never for more than 7 days.

## 9. Engagement layer: delivered

Specified in `05-engagement-psychology.md`. All of it is subject to that document's guardrails.

| Capability | Where | Status |
|---|---|---|
| **Story Map:** public stories by capture place in adaptive k-anonymous clusters (neighbourhood ≥ 3 people → area ≥ 2 → unplaced shelf; Safe Zones never placed). Lenses: radius, city, Roots, language, activity and Today's Prompt. Vibe, live-now glow, "N from your home region" and relay threads. Rate limited; paused in Couple Mode. | `discovery` | ✅ |
| **Today's Prompt:** per local date; staff-scheduled global or Roots prompts with a 30-prompt catalogue fallback; answers are moments with a `promptKey`; give-to-get unlock needs a public answer; truthful capped teaser | `prompts`, `moments` | ✅ |
| **Story Relays:** public reply-stories, one per person per relay, capped at 30, per-viewer visibility (blocks, paused location, Couple Mode), threads on the map, relay answers in the Local Pulse | `moments`, `discovery`, `pulse` | ✅ |
| **Connection Warmth:** levels from mutual days over 14 days, kind "quiet" state, starters from shared activities, roots and values | `connections`, `chat` | ✅ |
| **Global safety numbers:** profile country (ISO 3166-1) → emergency number on Date Mode, the trusted contact's page and texts | `profile`, `dates` | ✅ |

**Verification:** 103 automated tests pass on H2 and MySQL 8.0, with the two ffmpeg tests skipped in this environment. The new tests cover:
- that the map never places a lone person or a Safe-Zone story, and that it re-clusters after a block;
- the Roots lens never going below area level;
- the prompt staying locked after a friends-only answer;
- relay rules and per-viewer visibility;
- warmth never mentioning loss or streaks;
- per-country emergency numbers.

**Next (ordered):**
1. Festival Seasons (an automatic Roots prompt calendar).
2. A time-well-spent survey and WMA dashboards (`05` §5).
3. Right Now (behind Gate 2).
4. Plans & Rooms.
5. Memory Trail.
6. Weekly Recap.
