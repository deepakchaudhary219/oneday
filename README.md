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

Spring Boot 4.1 modular monolith. One package per service in the v1 catalog: `identity`, `verification`, `profile`, `geo`, `moments`, `discovery`, `signals`, `connections`, `chat`, `safety`, `pulse`, `privacy`.

### Run locally

Requirements: JDK 25 (the code also compiles and tests on JDK 21) and MySQL 8.

```bash
# MySQL on localhost:3306 (root/root by default; override with ONEDAY_DB_URL / ONEDAY_DB_USER / ONEDAY_DB_PASSWORD)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# API docs: http://localhost:8080/swagger-ui.html
```

The `dev` profile provides local-only secrets and the deterministic dev liveness verifier. It accepts the session tokens `dev-pass`, `dev-low-confidence` and `dev-looks-minor`. Any other deployment **must** set:

| Variable | Purpose |
|---|---|
| `ONEDAY_JWT_SECRET` | HS256 signing key (≥ 32 bytes) |
| `ONEDAY_LOCATION_SECRET` | Key for stable per-pair distance jitter (≥ 32 bytes) |
| `ONEDAY_VERIFICATION_PROVIDER` | Liveness vendor adapter id. Defaults to `none`, which never auto-approves. |

### Test

```bash
./mvnw test                      # JDK 25
./mvnw test -Djava.version=21    # JDK 21
```

Tests run the real security chain and the same Flyway migrations on H2 in MySQL mode. A controllable clock drives the 24 h story expiry and the 48 h Reaction Window.

### Try the core loop (dev profile)

```bash
B=http://localhost:8080
reg() { curl -s $B/auth/register -H 'Content-Type: application/json' -d "{\"email\":\"$1@example.com\",\"password\":\"correct-horse-battery\",\"dateOfBirth\":\"1998-04-12\",\"displayName\":\"$1\",\"consentVersion\":\"2026-09\"}" | jq -r .token; }
ver() { curl -s $B/verification/liveness -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d '{"sessionToken":"dev-pass"}' | jq -r .token.token; }
ASHA=$(ver $(reg asha)); RAVI=$(ver $(reg ravi))
for T in $ASHA $RAVI; do curl -s -X PUT $B/location -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"lat":12.9352,"lon":77.6245}' >/dev/null; done
M=$(curl -s $B/moments -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' \
  -d '{"kind":"PHOTO","mediaRef":"media/x.jpg","activityTag":"trek","shareScope":"PUBLIC_DISCOVERY","capturedLive":true}' | jq -r .id)
curl -s "$B/discover/constellation" -H "Authorization: Bearer $RAVI" | jq
S=$(curl -s $B/signals -H "Authorization: Bearer $RAVI" -H 'Content-Type: application/json' -d "{\"momentId\":\"$M\",\"reaction\":\"MADE_ME_SMILE\",\"activityRef\":\"trek\"}" | jq -r .id)
curl -s $B/signals/digest -H "Authorization: Bearer $ASHA" | jq
curl -s -X POST $B/signals/$S/reveal -H "Authorization: Bearer $ASHA" | jq
```
