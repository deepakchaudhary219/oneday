-- Multi-city (v3): launch stage and density-gated features per city. A person's city is found from their
-- snapped location cell; nothing finer is used and nothing is stored about which city someone is in.
CREATE TABLE cities (
    id           VARCHAR(40)  NOT NULL PRIMARY KEY,
    name         VARCHAR(80)  NOT NULL,
    country_code VARCHAR(2)   NOT NULL,
    center_lat   DOUBLE       NOT NULL,
    center_lon   DOUBLE       NOT NULL,
    radius_km    DOUBLE       NOT NULL,
    stage        VARCHAR(16)  NOT NULL,
    features     VARCHAR(300) NOT NULL,
    updated_at   DATETIME(6)  NOT NULL
);
CREATE INDEX idx_user_locations_box ON user_locations (cell_lat, cell_lon);
