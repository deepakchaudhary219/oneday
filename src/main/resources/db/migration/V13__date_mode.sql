-- Milestone 4 (v1.5, dating-ready): Date Mode (blueprint v2 §5.3, §6.2, §9).
-- Exact location exists only here, only between two people who both agreed to meet and both switched sharing
-- on, only inside the plan's time box, one current point per person (no history), purged when the plan closes.

CREATE TABLE meeting_points (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    category     VARCHAR(24)  NOT NULL,
    address      VARCHAR(300) NOT NULL,
    lat          DOUBLE       NOT NULL,
    lon          DOUBLE       NOT NULL,
    area         VARCHAR(8)   NOT NULL,
    safety_notes VARCHAR(300) NULL,
    active       BOOLEAN      NOT NULL,
    verified_by  VARCHAR(36)  NOT NULL,
    verified_at  DATETIME(6)  NOT NULL
);
CREATE INDEX idx_meeting_points_geo ON meeting_points (active, lat, lon);

CREATE TABLE date_plans (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    connection_id    VARCHAR(36)  NOT NULL,
    proposer_id      VARCHAR(36)  NOT NULL,
    partner_id       VARCHAR(36)  NOT NULL,
    meeting_point_id VARCHAR(36)  NULL,
    place_name       VARCHAR(160) NOT NULL,
    starts_at        DATETIME(6)  NOT NULL,
    ends_at          DATETIME(6)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    completed        BOOLEAN      NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    confirmed_at     DATETIME(6)  NULL,
    closed_at        DATETIME(6)  NULL,
    row_version      BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_date_plans_connection ON date_plans (connection_id, status);
CREATE INDEX idx_date_plans_status_ends ON date_plans (status, ends_at);
CREATE INDEX idx_date_plans_status_starts ON date_plans (status, starts_at);
CREATE INDEX idx_date_plans_proposer ON date_plans (proposer_id);
CREATE INDEX idx_date_plans_partner ON date_plans (partner_id);

-- One row per person per plan: their own consent, location, check-in, trusted contact and debrief.
CREATE TABLE date_participants (
    date_id                VARCHAR(36)  NOT NULL,
    user_id                VARCHAR(36)  NOT NULL,
    share_location         BOOLEAN      NOT NULL,
    lat                    DOUBLE       NULL,
    lon                    DOUBLE       NULL,
    location_at            DATETIME(6)  NULL,
    check_in_due_at        DATETIME(6)  NULL,
    check_in_prompted_at   DATETIME(6)  NULL,
    checked_in_at          DATETIME(6)  NULL,
    escalated_at           DATETIME(6)  NULL,
    escalation_reason      VARCHAR(24)  NULL,
    escalation_resolved_at DATETIME(6)  NULL,
    escalation_resolved_by VARCHAR(36)  NULL,
    contact_name           VARCHAR(60)  NULL,
    contact_phone          VARCHAR(20)  NULL,
    share_token_hash       VARCHAR(64)  NULL,
    home_safe_at           DATETIME(6)  NULL,
    debrief                VARCHAR(200) NULL,
    debrief_at             DATETIME(6)  NULL,
    purged_at              DATETIME(6)  NULL,
    PRIMARY KEY (date_id, user_id)
);
CREATE INDEX idx_date_participants_user ON date_participants (user_id);
CREATE INDEX idx_date_participants_check_in ON date_participants (check_in_due_at);
CREATE INDEX idx_date_participants_token ON date_participants (share_token_hash);
CREATE INDEX idx_date_participants_escalated ON date_participants (escalated_at);
