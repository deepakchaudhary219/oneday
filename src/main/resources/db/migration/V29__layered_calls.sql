-- Layered Video calls between Connections: voice first, then blurred video, then clear video, each step only
-- when both people are ready. Media is peer-to-peer (WebRTC, DTLS-SRTP) and never touches or is recorded by the
-- server; these rows are call metadata, kept 30 days for safety reports.
CREATE TABLE calls (
    id            VARCHAR(36) NOT NULL PRIMARY KEY,
    connection_id VARCHAR(36) NOT NULL,
    caller_id     VARCHAR(36) NOT NULL,
    callee_id     VARCHAR(36) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    caller_wants  VARCHAR(16) NOT NULL,
    callee_wants  VARCHAR(16) NOT NULL,
    end_reason    VARCHAR(16),
    started_at    DATETIME(6) NOT NULL,
    answered_at   DATETIME(6),
    ended_at      DATETIME(6)
);
CREATE INDEX idx_calls_caller ON calls (caller_id, status);
CREATE INDEX idx_calls_callee ON calls (callee_id, status);
CREATE INDEX idx_calls_open ON calls (status, started_at);
