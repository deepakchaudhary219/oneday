-- Milestone 2: push devices, the daily Local Pulse and in-app notices.

ALTER TABLE profiles ADD COLUMN time_zone VARCHAR(40) NOT NULL DEFAULT 'Asia/Kolkata';

CREATE TABLE devices (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    push_token   VARCHAR(300) NOT NULL,
    platform     VARCHAR(16)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    last_seen_at DATETIME(6)  NOT NULL,
    CONSTRAINT uq_devices_token UNIQUE (push_token)
);
CREATE INDEX idx_devices_user ON devices (user_id);

-- One Local Pulse per user per local day; the primary key makes delivery idempotent across replicas.
CREATE TABLE pulse_deliveries (
    user_id    VARCHAR(36) NOT NULL,
    local_date DATE        NOT NULL,
    sent       BOOLEAN     NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, local_date)
);

CREATE TABLE notices (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    kind       VARCHAR(32)  NOT NULL,
    message    VARCHAR(500) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    read_at    DATETIME(6)  NULL
);
CREATE INDEX idx_notices_user ON notices (user_id, created_at);
