-- Circle Live (v3): live video to your Connections or one Collaborative Thread, through an SFU (LiveKit-
-- compatible tokens). Never recorded; these rows are metadata, kept 30 days for safety reports.
CREATE TABLE live_sessions (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    host_id    VARCHAR(36) NOT NULL,
    thread_id  VARCHAR(36),
    title      VARCHAR(80),
    started_at DATETIME(6) NOT NULL,
    ended_at   DATETIME(6),
    end_reason VARCHAR(16)
);
CREATE INDEX idx_live_host ON live_sessions (host_id, ended_at);
CREATE INDEX idx_live_open ON live_sessions (ended_at, started_at);

CREATE TABLE live_viewers (
    session_id VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    joined_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id, user_id)
);
