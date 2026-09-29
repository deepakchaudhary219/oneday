-- Milestone 2: media processing worker. Clients upload to incoming/; only processed objects are served.
ALTER TABLE media_uploads ADD COLUMN incoming_key VARCHAR(120) NULL;
-- Rows from before processing existed were never cleaned, so they must not become attachable.
ALTER TABLE media_uploads ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'REJECTED';
ALTER TABLE media_uploads ADD COLUMN reject_reason VARCHAR(200) NULL;
ALTER TABLE media_uploads ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE media_uploads ADD COLUMN updated_at DATETIME(6) NULL;
UPDATE media_uploads SET reject_reason = 'Uploaded before media processing existed', updated_at = created_at;
CREATE INDEX idx_media_status ON media_uploads (status, updated_at);
