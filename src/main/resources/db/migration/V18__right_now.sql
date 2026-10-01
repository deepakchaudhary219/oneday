-- Right Now (blueprint v2 §6.1, v2 behind density Gate 2): opt in to a named activity for the next 30–120 minutes.
-- Nearby people see a band-level node; "I'm up for it too" is a request the poster accepts or silently declines.
CREATE TABLE right_now_sessions (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL,
    activity   VARCHAR(30) NOT NULL,
    starts_at  DATETIME(6) NOT NULL,
    ends_at    DATETIME(6) NOT NULL,
    ended_at   DATETIME(6) NULL
);
CREATE INDEX idx_right_now_active ON right_now_sessions (ends_at, ended_at);
CREATE INDEX idx_right_now_user ON right_now_sessions (user_id);

CREATE TABLE right_now_joins (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    joiner_id  VARCHAR(36) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_right_now_join UNIQUE (session_id, joiner_id)
);
CREATE INDEX idx_right_now_joins_joiner ON right_now_joins (joiner_id, created_at);
