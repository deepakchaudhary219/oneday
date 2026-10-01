-- Empathy Mirror: a message sent anyway after the pre-send reflection carries its tone category, so the
-- recipient can be asked "does this bother you?" with a one-tap report. NULL for ordinary messages.
ALTER TABLE messages ADD COLUMN tone_flag VARCHAR(24);
ALTER TABLE plan_messages ADD COLUMN tone_flag VARCHAR(24);
