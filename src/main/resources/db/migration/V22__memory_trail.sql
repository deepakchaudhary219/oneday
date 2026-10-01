-- Memory Trail (docs/05 §6): stories a person chose to keep, privately, past their 24 h. Kept stories survive the
-- retention purge with their location coarsened to the ~5 km area; their media is copied out of moments/ (which
-- the bucket lifecycle expires) into trail/.
ALTER TABLE moments ADD COLUMN kept BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE moments ADD COLUMN trail_media_ref VARCHAR(300) NULL;
CREATE INDEX idx_moments_kept ON moments (owner_id, kept, created_at);
