-- Real Value Ledger (blueprint v2 §7.3): a private read model fed by domain events. One row per real-world
-- outcome for one person; the source event id keeps the projection idempotent.

CREATE TABLE ledger_entries (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    kind            VARCHAR(32) NOT NULL,
    occurred_at     DATETIME(6) NOT NULL,
    source_event_id VARCHAR(36) NOT NULL,
    CONSTRAINT uq_ledger_source UNIQUE (user_id, kind, source_event_id)
);
CREATE INDEX idx_ledger_user ON ledger_entries (user_id, occurred_at);
