-- End-to-end encrypted Friend Mode chat (blueprint v2 §11). The server keeps public keys, per-device ciphertext
-- until delivery, and message metadata; it never holds a private key or a plaintext message.

-- One row per app install. Bound to the sign-in session that registered it: when that session ends (sign-out,
-- a lost phone signed out remotely, theft detection), the device stops receiving messages.
CREATE TABLE e2ee_devices (
    user_id                 VARCHAR(36)    NOT NULL,
    device_id               INT            NOT NULL,
    session_id              VARCHAR(36)    NOT NULL,
    registration_id         INT            NOT NULL,
    identity_key            VARBINARY(65)  NOT NULL,
    signed_prekey_id        INT            NOT NULL,
    signed_prekey           VARBINARY(65)  NOT NULL,
    signed_prekey_signature VARBINARY(128) NOT NULL,
    created_at              DATETIME(6)    NOT NULL,
    updated_at              DATETIME(6)    NOT NULL,
    PRIMARY KEY (user_id, device_id)
);

-- Single-use prekeys, each handed out at most once (claimed by a conditional DELETE).
CREATE TABLE e2ee_one_time_prekeys (
    user_id    VARCHAR(36)   NOT NULL,
    device_id  INT           NOT NULL,
    key_id     INT           NOT NULL,
    public_key VARBINARY(65) NOT NULL,
    PRIMARY KEY (user_id, device_id, key_id)
);

-- Ciphertext waiting for one device; deleted when the device acknowledges it, or after 30 days.
CREATE TABLE e2ee_envelopes (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    message_id          VARCHAR(36)     NOT NULL,
    conversation_id     VARCHAR(36)     NOT NULL,
    sender_user_id      VARCHAR(36)     NOT NULL,
    sender_device_id    INT             NOT NULL,
    recipient_user_id   VARCHAR(36)     NOT NULL,
    recipient_device_id INT             NOT NULL,
    type                VARCHAR(8)      NOT NULL,
    ciphertext          VARBINARY(12288) NOT NULL,
    created_at          DATETIME(6)     NOT NULL
);
CREATE INDEX idx_e2ee_inbox ON e2ee_envelopes (recipient_user_id, recipient_device_id, created_at);
CREATE INDEX idx_e2ee_envelope_conversation ON e2ee_envelopes (conversation_id);

-- Message metadata stays server-side so pacing, rhythm and Weekly Meaningful Actives keep working. The franking
-- commitment HMAC(k, plaintext) lets a recipient prove what they were sent when reporting it.
ALTER TABLE messages ADD COLUMN encrypted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE messages ADD COLUMN franking_commitment VARBINARY(32);
-- Once a conversation is encrypted it never accepts plaintext again (no downgrade).
ALTER TABLE conversations ADD COLUMN e2ee BOOLEAN NOT NULL DEFAULT FALSE;
-- A reported encrypted message whose franking commitment verified: the moderator sees what was really sent.
ALTER TABLE reports ADD COLUMN verified_evidence VARCHAR(4000);
