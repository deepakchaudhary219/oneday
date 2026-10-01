-- Time Capsules (v3): a message, optionally with a photo or video, sealed for yourself or a Connection until a
-- chosen day. Until then the recipient sees only that something is waiting and when it opens.
CREATE TABLE time_capsules (
    id                VARCHAR(36)   NOT NULL PRIMARY KEY,
    sender_id         VARCHAR(36)   NOT NULL,
    recipient_id      VARCHAR(36)   NOT NULL,
    connection_id     VARCHAR(36),
    message           VARCHAR(1000) NOT NULL,
    media_kind        VARCHAR(8),
    media_ref         VARCHAR(300),
    durable_media_ref VARCHAR(300),
    status            VARCHAR(16)   NOT NULL,
    opens_at          DATETIME(6)   NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    opened_at         DATETIME(6)
);
CREATE INDEX idx_capsules_sender ON time_capsules (sender_id, status);
CREATE INDEX idx_capsules_recipient ON time_capsules (recipient_id, status);
CREATE INDEX idx_capsules_due ON time_capsules (status, opens_at);
