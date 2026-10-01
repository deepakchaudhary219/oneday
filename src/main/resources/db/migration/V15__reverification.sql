-- Re-verification every 90 days (blueprint §21.4): a verified face must still be the person using the account.
-- One reminder before the deadline; after it the verification lapses (status EXPIRED) until the next check.
ALTER TABLE users ADD COLUMN reverify_reminded_at DATETIME(6) NULL;
CREATE INDEX idx_users_verified_at ON users (verification_status, verified_at);
