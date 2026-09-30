-- Couple Mode (blueprint v2 §7.4, "designed to graduate"). Each side's confirmation is private, like a
-- spark; only when both confirm does Couple Mode switch on, pausing both people's Discovery-Mode visibility.

ALTER TABLE connections ADD COLUMN couple_a BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE connections ADD COLUMN couple_b BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE connections ADD COLUMN couple_since DATETIME(6) NULL;
