# OneDay

A story-first, hyperlocal social network where friendship **and dating** can happen. Think of it as *the future of Snapchat*: camera-first and ephemeral, plus a **safe way to meet new people nearby**, a **privacy-graded map** organized by where you are *and where you're from*, and success measured by **real connections** rather than screen time.

> Working name: "OneDay" (the repository name). Everything public lasts about a day.

## Documentation

| Doc | What's in it |
|---|---|
| [`docs/01-market-and-feasibility.md`](docs/01-market-and-feasibility.md) | September 2026 market check (Snapchat, Instagram Map, Tinder, Bumble, Hinge, India), feasibility, the findings that change the original blueprint |
| [`docs/02-product-blueprint-v2.md`](docs/02-product-blueprint-v2.md) | Consolidated product spec: Dating Lens + Mutual Spark, the "Roots & Radius" map, Real Values, India-first requirements, release scope |
| [`docs/03-technical-architecture-v2.md`](docs/03-technical-architecture-v2.md) | Modular-monolith architecture, API surface, **location-privacy engineering**, data model, DPDP/IT Rules compliance |
| [`docs/04-implementation-plan.md`](docs/04-implementation-plan.md) | Milestones M0–M6, team, density gates, what each milestone delivered |
| [`docs/05-engagement-psychology.md`](docs/05-engagement-psychology.md) | Why people come back: the psychology behind every engagement feature, the dark patterns we refuse, and how we measure it |

These docs extend the original *Product & Psychology Blueprint* and *Technical Architecture & System Design Specification*.

## Milestone 1: backend core loop

```
live story ─► nearby Constellation (Layer 0) ─► Signal (Layer 1, budgeted, no free text)
          ─► recipient's digest (48 h Reaction Window) ─► Mutual Reveal (Layer 2)
          ─► Friend-Mode chat ─► private Mutual Spark ─► (block / soft exit at any point)
```

Spring Boot 4.1 modular monolith. One package per service in the v1 catalog: `identity`, `verification`, `profile`, `geo`, `moments`, `media`, `discovery`, `signals`, `connections`, `chat`, `safety`, `staff`, `pulse`, `privacy`, plus `events` (outbox), `dates` (Date Mode) and `ledger` (Real Value Ledger).

**Milestone 2 so far:**
- Redis-shared rate limits.
- A Trust & Safety staff console (manual verification review, report queue with SLA timers, suspensions, audit log).
- Pre-signed media uploads, with a worker that strips all metadata before anything is served.
- Phone OTP login.
- Deferred erasure under a safety hold.
- Local Pulse push notifications.
- A container image, a production-shaped `docker compose` stack and CI on H2, MySQL 8.4 and the full stack.
- Sign-in sessions: 15-minute access tokens, rotating refresh tokens with replay detection, and signing out of one or all devices.
- Grievance redressal to the Grievance Officer (IT Rules 3(2), DPDP), including appeals from suspended accounts.
- Observability: request ids on every response and log line, and Prometheus metrics with alertable backlog gauges.
- Domain events through a **transactional outbox**, with idempotent consumers (inbox), lease-based relaying across replicas, retries with backoff and a dead-letter queue for staff.

**Engagement layer** (see [`docs/05-engagement-psychology.md`](docs/05-engagement-psychology.md)):
- **Story Map** (`GET /map/stories`): public stories placed where they were captured. Clusters are adaptive and k-anonymous, so no person is ever pinned. Lenses: Roots, language, activity and Today's Prompt. Story Relays are drawn as threads. Private accounts can post public stories.
- **Today's Prompt** (`GET /prompts/today`): one local prompt a day plus staff-scheduled Roots/festival prompts. Nearby answers unlock after you answer publicly.
- **Story Relays**: answer a stranger's public story with your own (`replyToMomentId`, `GET /moments/{id}/relay`).
- **Connection Warmth** instead of streaks: no countdown and no loss; conversation starters from shared context.
- **Global from day one**: a country on the profile drives the emergency number on every safety surface.

**Milestone 4 (dating-ready v1.5) so far:**
- **Date Mode:** plans between Connections, exact location that is time-boxed and needs both people's consent, a trusted contact with a private live link, "Going OK?" check-ins that escalate, one-tap SOS (112), "home safe" end-of-date confirmation, and a Trust & Safety alert desk.
- **Safety-Verified Meeting Points**, curated by staff.
- **Mutual Debrief:** only the positive answers both people gave are revealed, and the safety answers stay private.
- **Couple Mode:** confirmed privately, and switched on only when both confirm; it pauses Discovery for both people.
- **Real Value Ledger:** a private monthly summary of real outcomes, built as an event-driven read model.

See [`docs/04-implementation-plan.md`](docs/04-implementation-plan.md) §7–§8.

### Run locally

Requirements: JDK 25 (the code also compiles and tests on JDK 21) and MySQL 8.4. No S3, Redis or ffmpeg is needed: the `dev` profile stands in for them.

```bash
# MySQL in Docker on localhost:3306 (the dev defaults are root/root; override with ONEDAY_DB_URL / ONEDAY_DB_USER / ONEDAY_DB_PASSWORD)
docker run -d --name oneday-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8.4
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run      # Windows: $env:SPRING_PROFILES_ACTIVE="dev"; .\mvnw.cmd spring-boot:run
# API docs: http://localhost:8080/swagger-ui.html
```

In IntelliJ, run `OnedayApplication` with the active profile `dev` (Run configuration → Active profiles). If port 3306 is taken by a local MySQL, map another port (`-p 3307:3306`) and set `ONEDAY_DB_URL=jdbc:mysql://localhost:3307/oneday?createDatabaseIfNotExist=true`.

The `dev` profile provides local-only secrets and local stand-ins:
- **Liveness:** a deterministic dev verifier that accepts the session tokens `dev-pass`, `dev-low-confidence` and `dev-looks-minor`.
- **SMS:** OTP codes are printed in the log.
- **Media storage:** a signed in-memory object store served by the app at `/dev-media/`.
- **Push:** notifications are printed in the log.

Any other deployment **must** set:

| Variable | Purpose |
|---|---|
| `ONEDAY_JWT_SECRET` | HS256 signing key (≥ 32 bytes). Also keys the OTP hashes. |
| `ONEDAY_LOCATION_SECRET` | Key for stable per-pair distance jitter and hashed probe-budget cells (≥ 32 bytes) |
| `ONEDAY_VERIFICATION_PROVIDER` | Liveness vendor adapter id. Defaults to `none`, which never auto-approves. |
| `ONEDAY_MEDIA_PROVIDER` | `s3` (AWS S3 or any S3-compatible store) or `none`. With `s3`, also set `ONEDAY_MEDIA_BUCKET`, `ONEDAY_MEDIA_REGION` (default `ap-south-1`, Mumbai), and optionally `ONEDAY_MEDIA_ENDPOINT` (a non-AWS store), `ONEDAY_MEDIA_PUBLIC_ENDPOINT` (the host phones use in signed URLs, when it differs from how the API reaches the store) and `ONEDAY_MEDIA_ACCESS_KEY` / `ONEDAY_MEDIA_SECRET_KEY`. Without static keys the default AWS credential chain is used. |
| `ONEDAY_PUSH_PROVIDER` | Push adapter for the Local Pulse and safety notices (`none` disables push; in-app notices still work) |
| `ONEDAY_FFMPEG` / `ONEDAY_FFPROBE` | Paths to ffmpeg/ffprobe for video processing. Without them, video uploads are refused rather than served unprocessed. |
| `ONEDAY_SMS_PROVIDER` | SMS adapter for phone login (`none` disables it). India needs a TRAI DLT-registered sender. |
| `ONEDAY_GRIEVANCE_OFFICER_NAME` / `_EMAIL` / `_ADDRESS` | The Grievance Officer's published contact (IT Rules 3(2), DPDP), served at `GET /grievances/officer`. Required before launch. |
| `MANAGEMENT_SERVER_PORT` | An internal-only port (e.g. `8081`) for health probes and Prometheus scraping (`/actuator/prometheus`, token-free only there). Without it, metrics need an admin token. |
| `ONEDAY_PUBLIC_URL` | Public HTTPS base of the API, used in the private link texted to a Date Mode trusted contact (`/date-share/…`) |
| `ONEDAY_API_DOCS` | `true` publishes `/v3/api-docs` and Swagger UI. Off by default outside the `dev` profile. |
| `ONEDAY_BOOTSTRAP_ADMIN_IDS` | Comma-separated **user ids** that act as the first Trust & Safety admins. Take the id from the `sub` of your own token. Ids, not emails: emails aren't ownership-verified yet. |

**Multiple replicas:** add the `redis` profile (`SPRING_PROFILES_ACTIVE=prod,redis`, plus `ONEDAY_REDIS_HOST` / `ONEDAY_REDIS_PORT` / `ONEDAY_REDIS_PASSWORD`) so rate limits and location probe budgets are shared.

**Media bucket:** keep it private. Add lifecycle rules that expire `moments/` after about 3 days (stories are ephemeral, and the rule backs up the best-effort deletes) and `incoming/` after 1 day.

### Run the whole stack in Docker

Only Docker is needed on the machine: the build happens inside Docker, and the S3-compatible store is a container too (no AWS account, no MinIO).

```bash
scripts/new-env.sh               # writes .env with random secrets
docker compose up --build -d     # API :8080, MySQL 8.4, Redis, SeaweedFS (S3 API on :8333)
docker compose run --rm smoke    # sign-up -> signed upload -> metadata stripped -> reveal -> sessions -> erasure
```

On Windows (PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\new-env.ps1
docker compose up --build -d
docker compose run --rm smoke
docker compose logs -f api       # follow the API log; docker compose down -v removes everything
```

The image (`Dockerfile`) is a JRE 25 runtime with ffmpeg, running as a non-root user. The compose stack uses the `redis` profile, real S3 signatures against SeaweedFS (MinIO no longer publishes community images), and the `dev` SMS, liveness and push providers so the loop works offline: OTP codes and pushes appear in `docker compose logs api`. The smoke test runs in its own container, so it needs no bash, curl or jq on the host. It is the same test CI runs. If you regenerate `.env`, run `docker compose down -v` first so MySQL and Redis start with the new passwords.

### Test

```bash
./mvnw test                      # JDK 25
./mvnw test -Djava.version=21    # JDK 21
```

Tests run the real security chain and the same Flyway migrations on H2 in MySQL mode. A controllable clock drives the 24 h story expiry and the 48 h Reaction Window. If `redis-server` and `ffmpeg` are on the `PATH`, the Redis implementations and video processing are tested against the real tools; otherwise those tests are skipped.

To run the same suite on real MySQL, point it at an empty database:

```bash
docker run -d --name oneday-mysql -p 3306:3306 -e MYSQL_DATABASE=oneday_test -e MYSQL_USER=oneday \
  -e MYSQL_PASSWORD=oneday -e MYSQL_RANDOM_ROOT_PASSWORD=yes mysql:8.4
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/oneday_test SPRING_DATASOURCE_USERNAME=oneday \
  SPRING_DATASOURCE_PASSWORD=oneday ./mvnw test
```

CI (`.github/workflows/ci.yml`) runs the suite on H2 and on MySQL 8.4 with ffmpeg and redis-server installed, and fails if any test is skipped. It then builds the image, starts the compose stack and runs `scripts/smoke-test.sh`.

### Try the core loop (dev profile)

```bash
B=http://localhost:8080
reg() { curl -s $B/auth/register -H 'Content-Type: application/json' -d "{\"email\":\"$1@example.com\",\"password\":\"correct-horse-battery\",\"dateOfBirth\":\"1998-04-12\",\"displayName\":\"$1\",\"consentVersion\":\"2026-09\"}" | jq -r .token; }
ver() { curl -s $B/verification/liveness -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d '{"sessionToken":"dev-pass"}' | jq -r .token.token; }
ASHA=$(ver $(reg asha)); RAVI=$(ver $(reg ravi))
for T in $ASHA $RAVI; do curl -s -X PUT $B/location -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"lat":12.9352,"lon":77.6245}' >/dev/null; done
# Photo: ticket -> PUT the file to the signed URL -> complete (metadata is stripped) -> publish. Any JPEG works.
SIZE=$(stat -c%s photo.jpg)
T=$(curl -s $B/media/uploads -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' -d "{\"kind\":\"PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":$SIZE}")
REF=$(echo "$T" | jq -r .mediaRef)
curl -s -X PUT "$(echo "$T" | jq -r .uploadUrl)" -H 'Content-Type: image/jpeg' --data-binary @photo.jpg
curl -s $B/media/uploads/complete -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' -d "{\"mediaRef\":\"$REF\"}" | jq
curl -s "$B/media/uploads/status?mediaRef=$REF" -H "Authorization: Bearer $ASHA" | jq   # READY
M=$(curl -s $B/moments -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' \
  -d "{\"kind\":\"PHOTO\",\"mediaRef\":\"$REF\",\"activityTag\":\"trek\",\"shareScope\":\"PUBLIC_DISCOVERY\",\"capturedLive\":true}" | jq -r .id)
curl -s "$B/discover/constellation" -H "Authorization: Bearer $RAVI" | jq
S=$(curl -s $B/signals -H "Authorization: Bearer $RAVI" -H 'Content-Type: application/json' -d "{\"momentId\":\"$M\",\"reaction\":\"MADE_ME_SMILE\",\"activityRef\":\"trek\"}" | jq -r .id)
curl -s $B/signals/digest -H "Authorization: Bearer $ASHA" | jq
curl -s -X POST $B/signals/$S/reveal -H "Authorization: Bearer $ASHA" | jq
```

### Sessions and grievances

```bash
# Sign-in returns a 15-minute access token and a refresh token; keep the refresh token in secure storage.
L=$(curl -s $B/auth/login -H 'Content-Type: application/json' -H 'User-Agent: OneDay/1.0 (Android 16)' \
  -d '{"email":"asha@example.com","password":"correct-horse-battery"}')
# Swap it before the access token expires. Each refresh token works once: replaying an old one ends the session.
curl -s $B/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$(echo "$L" | jq -r .refreshToken)\"}" | jq
curl -s $B/auth/sessions -H "Authorization: Bearer $ASHA" | jq                       # signed-in devices
curl -s -X POST $B/auth/logout-all -H "Authorization: Bearer $ASHA"                  # lost phone: sign out everywhere
# Complaints and appeals to the Grievance Officer (a suspended account can sign in and appeal too).
curl -s $B/grievances/officer | jq
curl -s $B/grievances -H "Authorization: Bearer $RAVI" -H 'Content-Type: application/json' \
  -d '{"category":"ACCOUNT_ACTION","description":"Please review my suspension."}' | jq  # reference + deadline
```

### Story Map, Today's Prompt and relays (dev profile)

```bash
P=$(curl -s $B/prompts/today -H "Authorization: Bearer $ASHA")                  # today's prompt + truthful teaser
echo "$P" | jq
KEY=$(echo "$P" | jq -r .promptKey)
curl -s $B/moments -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' \
  -d "{\"kind\":\"TEXT\",\"caption\":\"Filter coffee at the corner darshini\",\"activityTag\":\"coffee\",\"shareScope\":\"PUBLIC_DISCOVERY\",\"promptKey\":\"$KEY\"}" | jq
curl -s "$B/map/stories?scope=RADIUS" -H "Authorization: Bearer $RAVI" | jq    # clusters, never single people
curl -s "$B/map/stories?scope=ROOTS" -H "Authorization: Bearer $RAVI" | jq     # people from your home region
# Answer someone's public story with your own: a Story Relay.
curl -s $B/moments -H "Authorization: Bearer $RAVI" -H 'Content-Type: application/json' \
  -d "{\"kind\":\"TEXT\",\"caption\":\"Same here!\",\"shareScope\":\"PUBLIC_DISCOVERY\",\"replyToMomentId\":\"$M\"}" | jq
curl -s $B/moments/$M/relay -H "Authorization: Bearer $ASHA" | jq
```

The Story Map needs at least 3 people in a neighbourhood cell (or 2 in a ~5 km area) before it places anything there, so seed a few accounts when trying it locally.

### Date Mode (dev profile)

```bash
# Continue from the core loop above: $ASHA and $RAVI are connected. Get the connection id.
CID=$(curl -s $B/connections -H "Authorization: Bearer $RAVI" | jq -r '.[0].id')
START=$(date -u -d '+20 min' +%FT%TZ); END=$(date -u -d '+3 hour' +%FT%TZ)
D=$(curl -s $B/dates -H "Authorization: Bearer $RAVI" -H 'Content-Type: application/json' \
  -d "{\"connectionId\":\"$CID\",\"placeName\":\"Third Wave Coffee, Koramangala\",\"startsAt\":\"$START\",\"endsAt\":\"$END\"}" | jq -r .id)
curl -s -X POST $B/dates/$D/accept -H "Authorization: Bearer $ASHA" | jq .status              # CONFIRMED
# Exact location flows only while BOTH share, and only inside the time box (from 30 min before the start).
for T in $ASHA $RAVI; do curl -s -X PUT $B/dates/$D/sharing -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"enabled":true}' >/dev/null; done
curl -s -X PUT $B/dates/$D/location -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' -d '{"lat":12.9345,"lon":77.6260}' | jq
# A trusted contact gets a private link (the dev SMS sender prints it in the log). No account needed to open it.
curl -s -X PUT $B/dates/$D/trusted-contact -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' -d '{"name":"Meera","phone":"98765 43210"}' | jq
curl -s -X POST $B/dates/$D/sos -H "Authorization: Bearer $ASHA" | jq                       # 112 + alert, never shown to Ravi
curl -s -X POST $B/dates/$D/end -H "Authorization: Bearer $ASHA" | jq .status                # "home safe": location stops
curl -s $B/ledger -H "Authorization: Bearer $ASHA" | jq                                       # private Real Value Ledger
```

### Phone login (dev profile)

```bash
C=$(curl -s $B/auth/otp/request -H 'Content-Type: application/json' -d '{"phone":"98765 43210"}' | jq -r .challengeId)
# the dev SMS sender prints the 6-digit code in the server log
curl -s $B/auth/otp/verify -H 'Content-Type: application/json' -d "{\"challengeId\":\"$C\",\"phone\":\"9876543210\",\"code\":\"<code>\",\"displayName\":\"Priya\",\"dateOfBirth\":\"1997-02-14\",\"consentVersion\":\"2026-09\"}" | jq
```

