-- Milestone 2: phone OTP login. Accounts are identified by email or phone; phone-only accounts have no password.
ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_phone UNIQUE (phone);
ALTER TABLE users MODIFY email VARCHAR(254) NULL;
ALTER TABLE users MODIFY password_hash VARCHAR(100) NULL;

-- One-time codes. Phone and code are stored only as keyed HMACs; rows are swept after a day.
CREATE TABLE otp_challenges (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    phone_hash  VARCHAR(64) NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    attempts    INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL
);
CREATE INDEX idx_otp_phone ON otp_challenges (phone_hash, created_at);
CREATE INDEX idx_otp_created ON otp_challenges (created_at);
