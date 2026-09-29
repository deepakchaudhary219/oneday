-- Milestone 2: erasure deferred by a safety hold (open P0 report or recent enforcement).
ALTER TABLE users ADD COLUMN erasure_requested_at DATETIME(6) NULL;
ALTER TABLE users ADD COLUMN held_identifiers VARCHAR(300) NULL;
CREATE INDEX idx_users_erasure ON users (erasure_requested_at);
