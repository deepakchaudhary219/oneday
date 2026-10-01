-- Festival Seasons (docs/05-engagement-psychology.md §6.1): a multi-day window in which people from a home region
-- (or everyone, when home_region is null) get a festival prompt, and the Story Map labels festival stories.
-- Dates move every year (lunar calendars), so staff enter each season's dates instead of the app guessing them.
CREATE TABLE festival_seasons (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    name          VARCHAR(60)  NOT NULL,
    home_region   VARCHAR(8)   NULL,
    starts_on     DATE         NOT NULL,
    ends_on       DATE         NOT NULL,
    prompt_text   VARCHAR(140) NOT NULL,
    activity_hint VARCHAR(30)  NULL,
    created_by    VARCHAR(36)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL
);
CREATE INDEX idx_festival_seasons_dates ON festival_seasons (starts_on, ends_on);
