-- Device attestation challenges: single-use and short-lived, shared by every replica. A challenge is consumed
-- with one conditional DELETE, so it can never be used twice even under concurrent requests.
CREATE TABLE attestation_challenges (
    challenge  VARCHAR(64) NOT NULL PRIMARY KEY,
    expires_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_attestation_challenge_expiry ON attestation_challenges (expires_at);

-- Apple App Attest keys: the device's Secure Enclave public key, verified once against Apple's attestation, and
-- the assertion counter that must only ever increase (a repeat means a replayed or cloned assertion).
CREATE TABLE app_attest_keys (
    key_id       VARCHAR(64)   NOT NULL PRIMARY KEY,
    public_key   VARBINARY(200) NOT NULL,
    sign_count   BIGINT        NOT NULL,
    environment  VARCHAR(16)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    last_used_at DATETIME(6)
);
