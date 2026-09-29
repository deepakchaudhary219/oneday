-- OneDay Milestone 1 core schema (MySQL 8; also runs on H2 in MySQL mode for tests).
-- Privacy notes:
--   * user_locations holds only the CURRENT geohash-6 cell per user: no raw coordinates, no history.
--   * No column exists for caste, skin tone, income or religious identity (blueprint v2 section 7.2).

CREATE TABLE users (
    id                  VARCHAR(36)  NOT NULL PRIMARY KEY,
    email               VARCHAR(254) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    date_of_birth       DATE         NOT NULL,
    account_status      VARCHAR(16)  NOT NULL,
    verification_status VARCHAR(16)  NOT NULL,
    verified_at         DATETIME(6)  NULL,
    consent_version     VARCHAR(32)  NOT NULL,
    consented_at        DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE verification_attempts (
    id            VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id       VARCHAR(36) NOT NULL,
    provider      VARCHAR(32) NOT NULL,
    outcome       VARCHAR(24) NOT NULL,
    estimated_age INT         NOT NULL,
    confidence    DOUBLE      NOT NULL,
    created_at    DATETIME(6) NOT NULL
);
CREATE INDEX idx_verification_user ON verification_attempts (user_id, created_at);

CREATE TABLE profiles (
    user_id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    display_name        VARCHAR(40)  NOT NULL,
    bio                 VARCHAR(280) NULL,
    activities          VARCHAR(200) NULL,
    core_values         VARCHAR(200) NULL,
    languages           VARCHAR(40)  NULL,
    home_region         VARCHAR(8)   NULL,
    gender              VARCHAR(16)  NULL,
    interested_in       VARCHAR(40)  NULL,
    account_privacy     VARCHAR(16)  NOT NULL,
    dating_lens         BOOLEAN      NOT NULL,
    discretion_mode     BOOLEAN      NOT NULL,
    discovery_radius_km INT          NOT NULL,
    pulse_hour          INT          NOT NULL,
    safe_zone_prefix    VARCHAR(8)   NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_locations (
    user_id    VARCHAR(36) NOT NULL PRIMARY KEY,
    cell       VARCHAR(12) NOT NULL,
    cell_lat   DOUBLE      NOT NULL,
    cell_lon   DOUBLE      NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_locations_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE moments (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    owner_id        VARCHAR(36)  NOT NULL,
    kind            VARCHAR(16)  NOT NULL,
    caption         VARCHAR(200) NULL,
    activity_tag    VARCHAR(30)  NULL,
    media_ref       VARCHAR(300) NULL,
    preview_allowed BOOLEAN      NOT NULL,
    share_scope     VARCHAR(20)  NOT NULL,
    captured_live   BOOLEAN      NOT NULL,
    cell            VARCHAR(12)  NULL,
    cell_lat        DOUBLE       NULL,
    cell_lon        DOUBLE       NULL,
    created_at      DATETIME(6)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL
);
CREATE INDEX idx_moments_discovery ON moments (share_scope, expires_at, cell_lat, cell_lon);
CREATE INDEX idx_moments_owner ON moments (owner_id, created_at);

CREATE TABLE signals (
    id                VARCHAR(36) NOT NULL PRIMARY KEY,
    sender_id         VARCHAR(36) NOT NULL,
    recipient_id      VARCHAR(36) NOT NULL,
    moment_id         VARCHAR(36) NOT NULL,
    reaction          VARCHAR(24) NOT NULL,
    activity_ref      VARCHAR(30) NULL,
    status            VARCHAR(16) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    window_expires_at DATETIME(6) NOT NULL,
    resolved_at       DATETIME(6) NULL,
    CONSTRAINT uq_signals_sender_moment UNIQUE (sender_id, moment_id)
);
CREATE INDEX idx_signals_digest ON signals (recipient_id, status, window_expires_at);
CREATE INDEX idx_signals_budget ON signals (sender_id, created_at);
CREATE INDEX idx_signals_sweep ON signals (status, window_expires_at);

CREATE TABLE connections (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    user_a     VARCHAR(36) NOT NULL,
    user_b     VARCHAR(36) NOT NULL,
    origin     VARCHAR(16) NOT NULL,
    state      VARCHAR(16) NOT NULL,
    spark_a    BOOLEAN     NOT NULL,
    spark_b    BOOLEAN     NOT NULL,
    created_at DATETIME(6) NOT NULL,
    ended_at   DATETIME(6) NULL,
    CONSTRAINT uq_connections_pair UNIQUE (user_a, user_b)
);
CREATE INDEX idx_connections_user_b ON connections (user_b);

CREATE TABLE conversations (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    connection_id VARCHAR(36)  NOT NULL,
    seed_context  VARCHAR(120) NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_conversations_connection UNIQUE (connection_id),
    CONSTRAINT fk_conversations_connection FOREIGN KEY (connection_id) REFERENCES connections (id)
);

CREATE TABLE messages (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(36)   NOT NULL,
    sender_id       VARCHAR(36)   NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);
CREATE INDEX idx_messages_conversation ON messages (conversation_id, created_at);
CREATE INDEX idx_messages_sender ON messages (sender_id);

CREATE TABLE blocks (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    blocker_id VARCHAR(36) NOT NULL,
    blocked_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_blocks_pair UNIQUE (blocker_id, blocked_id)
);
CREATE INDEX idx_blocks_blocked ON blocks (blocked_id);

CREATE TABLE reports (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    reporter_id VARCHAR(36)   NULL,
    reported_id VARCHAR(36)   NOT NULL,
    category    VARCHAR(40)   NOT NULL,
    priority    VARCHAR(4)    NOT NULL,
    status      VARCHAR(16)   NOT NULL,
    target_type VARCHAR(16)   NOT NULL,
    details     VARCHAR(1000) NULL,
    created_at  DATETIME(6)   NOT NULL
);
CREATE INDEX idx_reports_queue ON reports (priority, status, created_at);
CREATE INDEX idx_reports_reporter ON reports (reporter_id);
