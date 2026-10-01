-- Public Figure accounts (v3): staff-verified creators, artists, athletes, journalists and officials whom people
-- can follow one way. Follower counts are visible only to the figure (no public metrics).
CREATE TABLE public_figures (
    user_id     VARCHAR(36)  NOT NULL PRIMARY KEY,
    handle      VARCHAR(30)  NOT NULL,
    public_name VARCHAR(60)  NOT NULL,
    category    VARCHAR(24)  NOT NULL,
    bio         VARCHAR(300),
    evidence    VARCHAR(500) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    applied_at  DATETIME(6)  NOT NULL,
    decided_at  DATETIME(6),
    decided_by  VARCHAR(36),
    CONSTRAINT uq_public_figure_handle UNIQUE (handle)
);
CREATE INDEX idx_public_figures_status ON public_figures (status, applied_at);

CREATE TABLE figure_follows (
    follower_id VARCHAR(36) NOT NULL,
    figure_id   VARCHAR(36) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (follower_id, figure_id)
);
CREATE INDEX idx_figure_follows_figure ON figure_follows (figure_id);
