-- Pulse Status (blueprint v2 §8): mood + emoji + optional note and Spotify track, friends-only, 24 h like Stories.
-- One current status per person; the public id changes on every update so a report targets the status it saw.
CREATE TABLE pulse_statuses (
    user_id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    id               VARCHAR(36)  NOT NULL,
    mood             VARCHAR(24)  NOT NULL,
    emoji            VARCHAR(32)  NOT NULL,
    note             VARCHAR(120),
    spotify_track_id VARCHAR(22),
    music_title      VARCHAR(200),
    set_at           DATETIME(6)  NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uq_pulse_status_id UNIQUE (id)
);
CREATE INDEX idx_pulse_status_expiry ON pulse_statuses (expires_at);
