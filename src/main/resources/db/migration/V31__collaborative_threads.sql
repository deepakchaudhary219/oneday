-- Collaborative Threads (v3): a shared story thread among friends for a trip, a wedding or a festival week.
-- Members may not know each other, so they are addressed by per-thread handles, never user ids.
CREATE TABLE threads (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    creator_id VARCHAR(36) NOT NULL,
    title      VARCHAR(60) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    ends_at    DATETIME(6) NOT NULL
);
CREATE INDEX idx_threads_ends ON threads (ends_at);

CREATE TABLE thread_members (
    thread_id  VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    invited_by VARCHAR(36) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (thread_id, user_id)
);
CREATE INDEX idx_thread_members_user ON thread_members (user_id, status);

CREATE TABLE thread_posts (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    thread_id         VARCHAR(36)  NOT NULL,
    author_id         VARCHAR(36)  NOT NULL,
    kind              VARCHAR(8)   NOT NULL,
    caption           VARCHAR(200),
    media_ref         VARCHAR(300),
    durable_media_ref VARCHAR(300),
    tone_flag         VARCHAR(24),
    created_at        DATETIME(6)  NOT NULL
);
CREATE INDEX idx_thread_posts_thread ON thread_posts (thread_id, created_at);
CREATE INDEX idx_thread_posts_author ON thread_posts (author_id);
