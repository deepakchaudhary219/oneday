-- Milestone 2: media upload tickets. Object keys are random and never contain a user id.
CREATE TABLE media_uploads (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    owner_id     VARCHAR(36)  NOT NULL,
    object_key   VARCHAR(120) NOT NULL,
    kind         VARCHAR(16)  NOT NULL,
    content_type VARCHAR(64)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uq_media_object_key UNIQUE (object_key)
);
CREATE INDEX idx_media_owner ON media_uploads (owner_id, created_at);
CREATE INDEX idx_moments_media ON moments (media_ref);
