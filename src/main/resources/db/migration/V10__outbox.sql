-- Milestone 2: transactional outbox + idempotent consumers (tech arch v2 §1.3, backlog item 4).
-- A domain event is written in the same transaction as the state change that caused it, then relayed to its
-- consumers at least once. Consumers record what they processed, so a redelivery is a no-op.

CREATE TABLE outbox_events (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    event_type      VARCHAR(64)   NOT NULL,
    schema_version  INT           NOT NULL,
    aggregate_id    VARCHAR(36)   NOT NULL,
    -- The accounts the event is about, comma-separated, so erasure can remove their events.
    user_ids        VARCHAR(80)   NOT NULL,
    payload         VARCHAR(4000) NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    attempts        INT           NOT NULL,
    occurred_at     DATETIME(6)   NOT NULL,
    next_attempt_at DATETIME(6)   NOT NULL,
    locked_by       VARCHAR(64)   NULL,
    locked_until    DATETIME(6)   NULL,
    dispatched_at   DATETIME(6)   NULL,
    last_error      VARCHAR(500)  NULL
);
CREATE INDEX idx_outbox_due ON outbox_events (status, next_attempt_at);
CREATE INDEX idx_outbox_dispatched ON outbox_events (status, dispatched_at);

-- The inbox: one row per (consumer, event) it has handled. Its primary key makes handling idempotent.
CREATE TABLE processed_events (
    handler      VARCHAR(64) NOT NULL,
    event_id     VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (handler, event_id)
);
CREATE INDEX idx_processed_events_at ON processed_events (processed_at);
