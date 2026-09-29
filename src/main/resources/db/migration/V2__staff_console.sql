-- Milestone 2: Trust & Safety staff console.

CREATE TABLE staff_members (
    user_id    VARCHAR(36) NOT NULL PRIMARY KEY,
    role       VARCHAR(16) NOT NULL,
    granted_by VARCHAR(36) NULL,
    granted_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Append-only audit trail of every staff decision (kept after erasure of the subject).
CREATE TABLE staff_actions (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    staff_user_id VARCHAR(36)  NOT NULL,
    action        VARCHAR(40)  NOT NULL,
    subject_type  VARCHAR(16)  NOT NULL,
    subject_id    VARCHAR(36)  NOT NULL,
    note          VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL
);
CREATE INDEX idx_staff_actions_time ON staff_actions (created_at);

ALTER TABLE reports ADD COLUMN assignee_id VARCHAR(36) NULL;
ALTER TABLE reports ADD COLUMN resolution VARCHAR(16) NULL;
ALTER TABLE reports ADD COLUMN resolution_note VARCHAR(500) NULL;
ALTER TABLE reports ADD COLUMN resolved_by VARCHAR(36) NULL;
ALTER TABLE reports ADD COLUMN resolved_at DATETIME(6) NULL;
CREATE INDEX idx_reports_reported ON reports (reported_id);
