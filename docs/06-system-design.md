# 06 · System Design for Scale

> How OneDay handles growth from one pilot city to many countries. It complements `03-technical-architecture-v2.md` (what each module does) with *how the system behaves under load*: the request path, data flow, consistency choices, and the order in which parts get extracted. Every mechanism below is implemented and tested in this repository unless it is marked **later**.

---

## 1. Shape of the system

```
Mobile app ── HTTPS ──► Load balancer ──► OneDay API (stateless replicas, N ≥ 2)
                                             │
       ┌──────────────┬──────────────────────┼───────────────────────┬───────────────────────┐
  MySQL primary   MySQL replica(s)        Redis                 Object store (S3)        Kafka (optional)
  writes + reads   opt-in heavy reads      rate limits,          pre-signed upload,       outbox relay →
  that must see    (Constellation, Story   probe budgets,        processing worker,       partitioned topic →
  own writes       Map, Heat, Prompt,      session-cache         private reads via        consumer groups
                   Ledger, Plans)          invalidation pub/sub  short-lived URLs         (+ DLT)
```

- **Modular monolith, service boundaries kept.** There is one deployable with one package per service (`identity`, `geo`, `moments`, `discovery`, `signals`, `dates`, `plans`, …). Packages talk to each other through service classes and domain events, never through each other's tables. Extracting a service is therefore a transport change (§6), not a rewrite.
- **Stateless API replicas.** No session state lives in memory:
  - shared counters live in Redis;
  - durable state lives in MySQL;
  - media goes straight between the phone and object storage.

  Scaling the API means adding replicas.

---

## 2. The request path

| Step | Mechanism | Why it scales |
|---|---|---|
| Authenticate | HS256 JWT, verified locally. The session check is served from `SessionLivenessCache` (15 s positive cache), with eviction on every session end, broadcast over Redis pub/sub. | Removes a primary-key lookup from every request; sign-out and theft detection still apply on the next request on every replica |
| Authorise | Spring Security scope gate (`verified`, staff) plus a service-level re-check | No call can bypass the contact gate |
| Rate limit | Redis Lua scripts (atomic, one round trip) | Limits are shared across replicas |
| Retry safety | `Idempotency-Key` on POSTs (stored in MySQL, 24 h) | Phones on flaky networks can retry without creating a second signal, plan or report |
| Read | `ReplicaReads.run(...)` plus a read-only transaction → replica. Everything else → primary. | Heavy, staleness-tolerant reads scale out; reads that must see the caller's own writes never lag |
| Write + side effects | State change and **outbox row** in one transaction; pushes, texts, projections and media copies happen after commit | Request latency never includes push, SMS or vendor I/O, and a vendor outage can't fail a user's request |
| Respond | Bounded pages everywhere (Constellation pages, digest batches, map clusters, relay length, Room pages) | Response cost per request is bounded by design, not by data size |

---

## 2a. Real-time delivery

```
service (after commit) ── RealtimeService ──► memory: SimpMessagingTemplate → sockets on this replica
                                         └──► redis: PUBLISH oneday:realtime → every replica → its own sockets
```

- **Server to client only.** Writes stay on the HTTP API, so validation, budgets, idempotency and the outbox apply unchanged.
- **Same auth as the API.** The socket uses the same JWT decoder and sign-in session check, and may subscribe only to `/user/queue/events`.
- **Sockets don't outlive access.** A socket is closed at token expiry, and within 30 s of its session ending.
- **Best effort by design.** A missed event is caught up on the next fetch; the database is the source of truth.
- **Next step at scale:** dedicated socket nodes behind the same Redis fan-out, or a managed pub/sub.

## 2b. End-to-end encrypted chat

```
sender device ── POST /conversations/{id}/encrypted ──► messages row (metadata + franking commitment)
                 (one envelope per device)            └► e2ee_envelopes (one row per recipient device)
                                                      └► socket ping "e2ee" (after commit, no content)
recipient device ── GET inbox ──► envelopes ── POST ack ──► deleted (the server keeps no copy)
```

- **Queue semantics:** the envelope table is a per-device queue (insert on send, delete on ack, 30-day TTL). At scale it moves to a wide-column store keyed by `(user, device)`, or to per-device Kafka partitions, without API change.
- **Consistency:**
  - device lists are read on the primary, in the send transaction;
  - a stale sender view fails fast with 409, so it is never silently partial;
  - one-time prekeys are claimed with a conditional DELETE, so no prekey is handed out twice across replicas.
- **What the server can still do:** pacing, rhythm, Weekly Meaningful Actives and abuse review of *reported* messages (franking). What it can't do is read anyone's chat.

## 3. Data flow: the transactional outbox

```
service method ──(same DB transaction)──► state rows + outbox_events row
                                              │ committed
OutboxRelay (every replica, lease per event) ─┘
        │ in-process (default)                       │ kafka (oneday.events.transport=kafka)
        ▼                                            ▼
  EventDispatcher ── handler + inbox row      topic oneday.domain-events (key = aggregateId)
  (one tx per handler, idempotent)                   │ consumer group, N partitions
                                                     ▼
                                              EventDispatcher (same handlers) ──fail──► retry ──► .DLT
```

- **Exactly-once effects from at-least-once delivery:**
  - `EventPublisher.publish` requires a transaction (`MANDATORY`), so there are no dual writes.
  - Each handler records `(handler, eventId)` in `processed_events` in the same transaction as its side effect, so redelivery is a no-op.
- **Ordering:** the record key is the aggregate id, so events about one plan, moment or person stay ordered on one partition. There is no global order and none is needed.
- **Failure handling:** retries back off exponentially; after `max-attempts` an event is parked as `DEAD` (admin requeue at `/staff/events`), or on Kafka goes to `<topic>.DLT`.
- **Operability:** the gauges `oneday_outbox_pending`, `oneday_outbox_dead` and `oneday_outbox_lag_seconds`.
- **Consumers today:**

  | Consumer | Purpose |
  |---|---|
  | Push delivery | Sends queued pushes |
  | Real Value Ledger projection | Private read model |
  | Weekly Meaningful Actives projection | North-star metric |
  | Trusted-contact alerts | Date Mode safety texts |
  | Plan/date cancellation on block | Safety |
  | Vouch removal on block | Safety |
  | Memory Trail media copy | Off-request media I/O |

---

## 4. Consistency choices (stated, not accidental)

| Data | Consistency | Reasoning |
|---|---|---|
| Sessions, blocks, verification, money-like budgets (signal budget, vouch budget) | Strong (primary, in-transaction) | Safety and abuse controls must never read stale state |
| Constellation, Story Map, Heat, Today's Prompt, Ledger, nearby Plans | Replica, eventually consistent (seconds) | A story appearing a second later is harmless; these are the heaviest reads |
| Ledger, Weekly Meaningful Actives | Eventually consistent projections | Built from events; they can be rebuilt by replaying events |
| Location cells, probe budgets | Primary, plus Redis for budgets | Anti-spoofing must be atomic |
| Exact Date Mode location | Primary only, one row per person, purged | The most sensitive data in the product |

---

## 5. Hot spots and how they're handled

| Hot spot | Today | When it grows (**later**) |
|---|---|---|
| Discovery and the Story Map (geo box queries) | Snapped cell centres with a bounding-box prefilter on indexed columns; replica reads; per-viewer rate limits; bounded results | A `moments_active` geo index in OpenSearch, or Redis GEO per city (tech arch v2 §1.3 trigger: discovery p95 > 150 ms) |
| Local Pulse (a daily push to everyone) | One indexed query per *time zone in use* for "whose hour is it now"; one claim row per (user, local date) for idempotency across replicas; pushes through the outbox | Shard the job by zone across replicas |
| Pushes and texts | Async through the outbox, so a vendor outage only builds a backlog (watch `outbox_lag`) | Kafka consumer group dedicated to notifications |
| Per-request session check | Positive cache plus pub/sub invalidation | — |
| Moment, signal and message volume | MySQL with time-ordered UUIDv7 keys (good InnoDB locality); expired stories purged after 14 days | Cassandra tables `moments_by_geohash`, `signals_by_recipient`, `messages_by_conversation` (trigger: ~5k writes/s or p99 > 50 ms) |
| Media | Pre-signed upload and download; bytes never touch the API; processing worker pool | A separate worker deployment (it needs ffmpeg) and a CDN in front of the bucket |

---

## 6. Extraction order (when a trigger fires)

1. **Notifications service:** it already consumes only events (`PushRequested`, plus safety alerts). Point it at the Kafka topic and run it separately.
2. **Media worker:** the same image in a different deployment, scaled on queue depth.
3. **Discovery read service:** it reads the replica plus a geo index and owns no writes.
4. **Projections** (Ledger, WMA) move to ClickHouse when analytics load shows up on MySQL.

Each step changes a deployment and a transport. The domain code stays the same, because modules already communicate through services and events.

---

## 7. Abuse resistance at scale

- **Device attestation** on signup, phone sign-in and location updates: Play Integrity (Android) and App Attest (iOS) behind one port, chosen by token shape. It is rolled out `monitor → enforce` behind a metric (`oneday_attestation{outcome}`, `oneday_app_attest{step,outcome}`). App Attest challenges live in MySQL and are consumed atomically, and key counters advance with a compare-and-set UPDATE, so replay protection holds across replicas with no sticky sessions.
- **Budgets on every approach channel:** signals, Right Now requests, plan requests, vouches.
- **Location privacy holds under scale:** cell snapping, stable per-pair jitter, probe budgets and k-anonymous map clusters.
- **Idempotency** prevents duplicate side effects from retries.
- **Re-verification every 90 days.**

---

## 8. Local development (everything runs on one machine)

| Need | Local stand-in |
|---|---|
| MySQL, Redis, S3 | `docker compose up` (MySQL 8.4, Redis, SeaweedFS), or H2 plus in-memory stores in the `dev` profile |
| Kafka | Optional: `SPRING_PROFILES_ACTIVE=dev,kafka` with any local broker. Tests use an embedded broker. |
| SMS, push, liveness, attestation | `dev` providers (logged, deterministic) |
| Vendor adapters (FCM, MSG91, Play Integrity) | Tested against a local fake vendor server (`src/test/.../integrations/FakeVendorServer`) |

Deployment (Indian cloud region, separate worker and notification deployments, managed MySQL with replicas, managed Kafka) is deliberately out of scope until development is complete.
