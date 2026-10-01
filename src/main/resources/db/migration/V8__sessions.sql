-- Milestone 2: sign-in sessions. Access tokens are short-lived JWTs naming their session; the session holds
-- only a SHA-256 of the current refresh secret, which rotates on every use.

CREATE TABLE sessions (
    id            VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id       VARCHAR(36) NOT NULL,
    refresh_hash  CHAR(64)    NOT NULL,
    -- The secret this one replaced, accepted briefly so a retried refresh (lost response) isn't taken for theft.
    previous_hash CHAR(64)    NULL,
    rotated_at    DATETIME(6) NULL,
    device        VARCHAR(80) NULL,
    created_at    DATETIME(6) NOT NULL,
    last_used_at  DATETIME(6) NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    ended_at      DATETIME(6) NULL,
    end_reason    VARCHAR(24) NULL
);
CREATE INDEX idx_sessions_user ON sessions (user_id, ended_at);
