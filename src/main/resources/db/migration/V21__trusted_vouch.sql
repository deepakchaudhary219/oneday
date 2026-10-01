-- Trusted Vouch (blueprint v2 §11): a Connection you've genuinely talked with vouches for you. Strangers see only a
-- capped count, never who. A block or erasure removes vouches both ways.
CREATE TABLE vouches (
    voucher_id VARCHAR(36) NOT NULL,
    vouchee_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (voucher_id, vouchee_id)
);
CREATE INDEX idx_vouches_vouchee ON vouches (vouchee_id);
