-- Spotlight Replies (v3): a story's owner highlights a Story Relay answer; it shows only once its author agrees.
CREATE TABLE spotlights (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    root_moment_id  VARCHAR(36) NOT NULL,
    reply_moment_id VARCHAR(36) NOT NULL,
    owner_id        VARCHAR(36) NOT NULL,
    replier_id      VARCHAR(36) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT uq_spotlight_reply UNIQUE (reply_moment_id)
);
CREATE INDEX idx_spotlights_root ON spotlights (root_moment_id, status);
CREATE INDEX idx_spotlights_replier ON spotlights (replier_id, status);
