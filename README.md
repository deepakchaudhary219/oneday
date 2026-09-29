# OneDay

A story-first, hyperlocal social network where friendship **and dating** can happen. Think of it as *the future of Snapchat*: camera-first and ephemeral, plus a **safe way to meet new people nearby**, a **privacy-graded map** organized by where you are *and where you're from*, and success measured by **real connections** rather than screen time.

> Working name: "OneDay" (the repository name). Everything public lasts about a day.

## Documentation

| Doc | What's in it |
|---|---|
| [`docs/01-market-and-feasibility.md`](docs/01-market-and-feasibility.md) | September 2026 market check (Snapchat, Instagram Map, Tinder, Bumble, Hinge, India), feasibility, the findings that change the original blueprint |
| [`docs/02-product-blueprint-v2.md`](docs/02-product-blueprint-v2.md) | Consolidated product spec: Dating Lens + Mutual Spark, the "Roots & Radius" map, Real Values, India-first requirements, release scope |
| [`docs/03-technical-architecture-v2.md`](docs/03-technical-architecture-v2.md) | Modular-monolith architecture, API surface, **location-privacy engineering**, data model, DPDP/IT Rules compliance |
| [`docs/04-implementation-plan.md`](docs/04-implementation-plan.md) | Milestones M0–M6, team, density gates, what Milestone 1 delivers |

These docs extend the original *Product & Psychology Blueprint* and *Technical Architecture & System Design Specification*.

## Milestone 1: backend core loop

```
live story ─► nearby Constellation (Layer 0) ─► Signal (Layer 1, budgeted, no free text)
          ─► recipient's digest (48 h Reaction Window) ─► Mutual Reveal (Layer 2)
          ─► Friend-Mode chat ─► private Mutual Spark ─► (block / soft exit at any point)
```

Spring Boot 4.1 modular monolith. One package per service in the v1 catalog: `identity`, `verification`, `profile`, `geo`, `moments`, `media`, `discovery`, `signals`, `connections`, `chat`, `safety`, `staff`, `pulse`, `privacy`.

**Milestone 2 so far:**
- Redis-shared rate limits.
- A Trust & Safety staff console (manual verification review, report queue with SLA timers, suspensions, audit log).
- Pre-signed media uploads with ownership checks.
- Phone OTP login.

See [`docs/04-implementation-plan.md`](docs/04-implementation-plan.md) §7.

### Run locally

Requirements: JDK 25 (the code also compiles and tests on JDK 21) and MySQL 8.

```bash
# MySQL on localhost:3306 (root/root by default; override with ONEDAY_DB_URL / ONEDAY_DB_USER / ONEDAY_DB_PASSWORD)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# API docs: http://localhost:8080/swagger-ui.html
```

The `dev` profile provides local-only secrets and local stand-ins:
- **Liveness:** a deterministic dev verifier that accepts the session tokens `dev-pass`, `dev-low-confidence` and `dev-looks-minor`.
- **SMS:** OTP codes are printed in the log.
- **Media storage:** URLs are fake, under `media.dev.invalid`.

Any other deployment **must** set:

| Variable | Purpose |
|---|---|
| `ONEDAY_JWT_SECRET` | HS256 signing key (≥ 32 bytes). Also keys the OTP hashes. |
| `ONEDAY_LOCATION_SECRET` | Key for stable per-pair distance jitter and hashed probe-budget cells (≥ 32 bytes) |
| `ONEDAY_VERIFICATION_PROVIDER` | Liveness vendor adapter id. Defaults to `none`, which never auto-approves. |
| `ONEDAY_MEDIA_PROVIDER` | `s3` (AWS S3 or MinIO) or `none`. With `s3`, also set `ONEDAY_MEDIA_BUCKET`, `ONEDAY_MEDIA_REGION` (default `ap-south-1`, Mumbai), and optionally `ONEDAY_MEDIA_ENDPOINT` (MinIO) and `ONEDAY_MEDIA_ACCESS_KEY` / `ONEDAY_MEDIA_SECRET_KEY`. Without static keys the default AWS credential chain is used. |
| `ONEDAY_SMS_PROVIDER` | SMS adapter for phone login (`none` disables it). India needs a TRAI DLT-registered sender. |
| `ONEDAY_BOOTSTRAP_ADMIN_IDS` | Comma-separated **user ids** that act as the first Trust & Safety admins. Take the id from the `sub` of your own token. Ids, not emails: emails aren't ownership-verified yet. |

**Multiple replicas:** add the `redis` profile (`SPRING_PROFILES_ACTIVE=prod,redis`, plus `ONEDAY_REDIS_HOST` / `ONEDAY_REDIS_PORT` / `ONEDAY_REDIS_PASSWORD`) so rate limits and location probe budgets are shared.

**Media bucket:** keep it private, and add a lifecycle rule that expires `moments/` after about 3 days. Stories are ephemeral, and the rule backs up the best-effort deletes. Until the media-processing worker lands, clients must strip EXIF/GPS metadata before uploading.

### Test

```bash
./mvnw test                      # JDK 25
./mvnw test -Djava.version=21    # JDK 21
```

Tests run the real security chain and the same Flyway migrations on H2 in MySQL mode. A controllable clock drives the 24 h story expiry and the 48 h Reaction Window. If `redis-server` is on the `PATH`, the Redis implementations are also tested against a throwaway instance; otherwise those tests are skipped.

### Try the core loop (dev profile)

```bash
B=http://localhost:8080
reg() { curl -s $B/auth/register -H 'Content-Type: application/json' -d "{\"email\":\"$1@example.com\",\"password\":\"correct-horse-battery\",\"dateOfBirth\":\"1998-04-12\",\"displayName\":\"$1\",\"consentVersion\":\"2026-09\"}" | jq -r .token; }
ver() { curl -s $B/verification/liveness -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d '{"sessionToken":"dev-pass"}' | jq -r .token.token; }
ASHA=$(ver $(reg asha)); RAVI=$(ver $(reg ravi))
for T in $ASHA $RAVI; do curl -s -X PUT $B/location -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"lat":12.9352,"lon":77.6245}' >/dev/null; done
# 1) get an upload ticket (the app would now PUT the photo to .uploadUrl), 2) publish the moment with its mediaRef
REF=$(curl -s $B/media/uploads -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' -d '{"kind":"PHOTO","contentType":"image/jpeg","sizeBytes":250000}' | jq -r .mediaRef)
M=$(curl -s $B/moments -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' \
  -d "{\"kind\":\"PHOTO\",\"mediaRef\":\"$REF\",\"activityTag\":\"trek\",\"shareScope\":\"PUBLIC_DISCOVERY\",\"capturedLive\":true}" | jq -r .id)
curl -s "$B/discover/constellation" -H "Authorization: Bearer $RAVI" | jq
S=$(curl -s $B/signals -H "Authorization: Bearer $RAVI" -H 'Content-Type: application/json' -d "{\"momentId\":\"$M\",\"reaction\":\"MADE_ME_SMILE\",\"activityRef\":\"trek\"}" | jq -r .id)
curl -s $B/signals/digest -H "Authorization: Bearer $ASHA" | jq
curl -s -X POST $B/signals/$S/reveal -H "Authorization: Bearer $ASHA" | jq
```

### Phone login (dev profile)

```bash
C=$(curl -s $B/auth/otp/request -H 'Content-Type: application/json' -d '{"phone":"98765 43210"}' | jq -r .challengeId)
# the dev SMS sender prints the 6-digit code in the server log
curl -s $B/auth/otp/verify -H 'Content-Type: application/json' -d "{\"challengeId\":\"$C\",\"phone\":\"9876543210\",\"code\":\"<code>\",\"displayName\":\"Priya\",\"dateOfBirth\":\"1997-02-14\",\"consentVersion\":\"2026-09\"}" | jq
```

