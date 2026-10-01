-- Engagement layer (docs/05-engagement-psychology.md): Today's Prompt, Story Relays, the Story Map, and a
-- country on the profile so safety numbers are right from day one in every market.

ALTER TABLE profiles ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'IN';

-- One prompt per day. A row with a region applies to people whose home region matches (Roots prompts,
-- e.g. Onam for IN-KL); a row without one applies to everyone. Days without a row use the built-in catalogue.
CREATE TABLE daily_prompts (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    prompt_date   DATE         NOT NULL,
    home_region   VARCHAR(8)   NULL,
    text          VARCHAR(140) NOT NULL,
    activity_hint VARCHAR(30)  NULL,
    created_by    VARCHAR(36)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL
);
CREATE INDEX idx_daily_prompts_date ON daily_prompts (prompt_date, home_region);

-- A moment can answer today's prompt (prompt_key: a scheduled prompt id or 'catalog:<yyyy-mm-dd>') and/or
-- join a Story Relay started by another public moment.
ALTER TABLE moments ADD COLUMN prompt_key VARCHAR(48) NULL;
ALTER TABLE moments ADD COLUMN relay_root_id VARCHAR(36) NULL;
ALTER TABLE moments ADD COLUMN reply_to_id VARCHAR(36) NULL;
ALTER TABLE moments ADD COLUMN relay_depth INT NOT NULL DEFAULT 0;
CREATE INDEX idx_moments_prompt ON moments (prompt_key, expires_at);
CREATE INDEX idx_moments_relay ON moments (relay_root_id, created_at);
