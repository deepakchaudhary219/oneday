-- Scale: the Local Pulse job asks "whose chosen hour is it now, in their own zone?" instead of scanning
-- every user with a device; idempotency keys make client retries of POSTs safe.
CREATE INDEX idx_profiles_pulse ON profiles (time_zone, pulse_hour);

CREATE TABLE idempotency_keys (
    user_id      VARCHAR(36)   NOT NULL,
    idem_key     VARCHAR(64)   NOT NULL,
    request_hash VARCHAR(64)   NOT NULL,
    status       INT           NULL,
    content_type VARCHAR(100)  NULL,
    body         MEDIUMTEXT    NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (user_id, idem_key)
);
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);
