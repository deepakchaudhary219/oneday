-- Milestone 2: grievance redressal (IT Rules 2021 rule 3(2), DPDP Act 2023 s.13). Complaints and appeals
-- addressed to the Grievance Officer; acknowledged on filing, answered within the category's deadline.

CREATE TABLE grievances (
    id           VARCHAR(36)   NOT NULL PRIMARY KEY,
    reference    VARCHAR(16)   NOT NULL,
    user_id      VARCHAR(36)   NOT NULL,
    category     VARCHAR(24)   NOT NULL,
    subject_ref  VARCHAR(64)   NULL,
    description  VARCHAR(2000) NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    resolve_by   DATETIME(6)   NOT NULL,
    resolved_at  DATETIME(6)   NULL,
    resolved_by  VARCHAR(36)   NULL,
    outcome      VARCHAR(16)   NULL,
    response     VARCHAR(2000) NULL,
    CONSTRAINT uq_grievances_reference UNIQUE (reference)
);
CREATE INDEX idx_grievances_open ON grievances (status, resolve_by);
CREATE INDEX idx_grievances_user ON grievances (user_id, created_at);
