-- AMA Corridors (v3): a Public Figure answers questions for a time-boxed window, open to everyone or to people
-- whose Roots are in one home region ("Keralites everywhere"). Vote counts are never shown publicly.
CREATE TABLE amas (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    host_id         VARCHAR(36)  NOT NULL,
    title           VARCHAR(100) NOT NULL,
    corridor_region VARCHAR(10),
    starts_at       DATETIME(6)  NOT NULL,
    ends_at         DATETIME(6)  NOT NULL,
    cancelled       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL
);
CREATE INDEX idx_amas_window ON amas (ends_at, starts_at);

CREATE TABLE ama_questions (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    ama_id      VARCHAR(36)   NOT NULL,
    asker_id    VARCHAR(36)   NOT NULL,
    body        VARCHAR(280)  NOT NULL,
    anonymous   BOOLEAN       NOT NULL,
    hidden      BOOLEAN       NOT NULL DEFAULT FALSE,
    tone_flag   VARCHAR(24),
    answer      VARCHAR(2000),
    answered_at DATETIME(6),
    created_at  DATETIME(6)   NOT NULL
);
CREATE INDEX idx_ama_questions_ama ON ama_questions (ama_id, created_at);
CREATE INDEX idx_ama_questions_asker ON ama_questions (asker_id);

CREATE TABLE ama_votes (
    question_id VARCHAR(36) NOT NULL,
    voter_id    VARCHAR(36) NOT NULL,
    PRIMARY KEY (question_id, voter_id)
);
