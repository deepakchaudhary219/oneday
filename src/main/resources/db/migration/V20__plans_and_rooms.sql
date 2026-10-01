-- Plans & Rooms (blueprint v2 §11, v2): small public meet-ups (3–12 people) at Safety-Verified Meeting Points.
-- Joining is a request the host approves; declines are silent. Approved members share a temporary Room that is
-- deleted 24 h after the plan ends.
CREATE TABLE plans (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    host_id          VARCHAR(36)  NOT NULL,
    meeting_point_id VARCHAR(36)  NOT NULL,
    activity         VARCHAR(30)  NOT NULL,
    title            VARCHAR(80)  NOT NULL,
    roots_region     VARCHAR(8)   NULL,
    capacity         INT          NOT NULL,
    starts_at        DATETIME(6)  NOT NULL,
    ends_at          DATETIME(6)  NOT NULL,
    lat              DOUBLE       NOT NULL,
    lon              DOUBLE       NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    row_version      BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_plans_open ON plans (status, starts_at, lat, lon);
CREATE INDEX idx_plans_host ON plans (host_id, status);

CREATE TABLE plan_members (
    plan_id    VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (plan_id, user_id)
);
CREATE INDEX idx_plan_members_user ON plan_members (user_id, created_at);

CREATE TABLE plan_messages (
    id         VARCHAR(36)   NOT NULL PRIMARY KEY,
    plan_id    VARCHAR(36)   NOT NULL,
    sender_id  VARCHAR(36)   NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)   NOT NULL
);
CREATE INDEX idx_plan_messages_plan ON plan_messages (plan_id, created_at);
