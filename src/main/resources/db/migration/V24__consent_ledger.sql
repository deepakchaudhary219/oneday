-- DPDP Act 2023 s.6: consent per purpose, withdrawable as easily as it was given, provable by the fiduciary.
-- Append-only: the current state of a purpose is its latest row. Pseudonymised (not deleted) on erasure, because
-- the burden of proving consent stays with the Data Fiduciary (s.6(10)).
CREATE TABLE consent_records (
    id             VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id        VARCHAR(80) NOT NULL,
    purpose        VARCHAR(32) NOT NULL,
    action         VARCHAR(16) NOT NULL,
    notice_version VARCHAR(16) NOT NULL,
    source         VARCHAR(24) NOT NULL,
    created_at     DATETIME(6) NOT NULL
);
CREATE INDEX idx_consent_user_purpose ON consent_records (user_id, purpose, created_at);
