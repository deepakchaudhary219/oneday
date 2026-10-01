-- Honest measurement (docs/05-engagement-psychology.md §5): Weekly Meaningful Actives and the
-- "was your time well spent?" guardrail.

-- One row per person per ISO week (Monday, UTC) in which they had a two-way interaction recorded by an event
-- (reveal, mutual spark, couple, relay answer, date). Two-way conversations are counted from messages directly.
CREATE TABLE weekly_actives (
    user_id    VARCHAR(36) NOT NULL,
    week_start DATE        NOT NULL,
    first_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, week_start)
);
CREATE INDEX idx_weekly_actives_week ON weekly_actives (week_start);

CREATE TABLE wellbeing_answers (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL,
    well_spent BOOLEAN     NOT NULL,
    created_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_wellbeing_user ON wellbeing_answers (user_id, created_at);
CREATE INDEX idx_wellbeing_created ON wellbeing_answers (created_at);
