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
- ✅ Staff audit log and report-handling console with SLA timers. ⏳ Observability (OpenTelemetry), breach runbook, grievance flow.
- ⏳ CI: GitHub Actions running tests against MySQL (Testcontainers). Deploy to an Indian cloud region.
- **Exit:** load test at 5× the expected pilot peak. Staff can execute the takedown SLA.

### M3 · Mobile app v1 (≈ 8–12 weeks, starts once M1 APIs are frozen)
- Camera-first capture (live only in Discovery Mode). Share-scope picker.
- Constellation view (radius dial, scopes, activity grouping, "caught up" screen). Heat overlay.
- Digest (Local Pulse), Signal composer (4 reactions + activity reference), reveal and pass.
- Friend-Mode chat, Investment Balance, soft exit, block/report sheets.
- Preference Center (blueprint §32.1): radius, notification cadence, Dating Lens, discretion, Safe Zones, pause.
- Vernacular: English, Hindi and Kannada at pilot.
- **Exit:** internal dog-food with 50+ staff and friends for 2 weeks with no P0 safety bugs.

### M4 · Dating-ready v1.5 (≈ 4–6 weeks)
- Date Mode: plan confirmation, **time-boxed exact location** (both consent, auto-expire), trusted contact share, scheduled check-in, **one-tap 112**, end-of-date confirmation.
- Safety-Verified Meeting Points (partner venues), Discovery Trails.
- Mutual Debrief (reveals only overlapping positive answers). Couple Mode.
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

Done in M2 so far: Redis state store, staff console with manual review, media uploads with ownership checks, phone OTP, the media processing worker, deferred erasure under a safety hold, and Local Pulse push notifications (see §7).

1. **Vendor adapters** behind the ports that already exist: liveness/age estimation (replaces `DevLivenessVerifier`), a TRAI DLT-registered SMS sender, and FCM/APNs push.
2. **CI and deployment:** GitHub Actions running the suite against MySQL (Testcontainers), with `redis-server` and `ffmpeg` in the image so those tests run too. A separate media-worker deployment (it needs ffmpeg). Deploy to an Indian cloud region.
3. Refresh tokens + revocation list. Device attestation (Play Integrity / App Attest) on signup and location updates.
4. Outbox table + domain events (`MomentPublished`, `SignalCreated`, `MutualReveal`, `UserBlocked`) to prepare for Kafka. Notifications are currently sent inline.
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

**Verification:** 75 automated tests pass, with nothing skipped in this environment. They include:
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
