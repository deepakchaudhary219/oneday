-- OneDay Plus (blueprint v2 §7.5): an honest subscription (UPI Autopay via Razorpay in India). Safety is never
-- paywalled; Plus adds only convenience (wider radius, more hosted plans). Payment records are kept for tax law,
-- detached from the person on erasure.
CREATE TABLE subscriptions (
    id                       VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id                  VARCHAR(36)  NOT NULL,
    provider                 VARCHAR(16)  NOT NULL,
    provider_subscription_id VARCHAR(64)  NOT NULL,
    status                   VARCHAR(16)  NOT NULL,
    current_end              DATETIME(6)  NULL,
    cancel_at_cycle_end      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    CONSTRAINT uq_subscriptions_provider UNIQUE (provider, provider_subscription_id)
);
CREATE INDEX idx_subscriptions_user ON subscriptions (user_id, status);

-- Webhook deliveries already applied (providers retry; applying twice must be harmless).
CREATE TABLE payment_webhook_events (
    id          VARCHAR(100) NOT NULL PRIMARY KEY,
    received_at DATETIME(6)  NOT NULL
);
