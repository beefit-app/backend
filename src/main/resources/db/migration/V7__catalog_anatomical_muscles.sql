ALTER TABLE exercises
    ADD COLUMN primary_muscles varchar[] NOT NULL DEFAULT '{}',
    ADD COLUMN secondary_muscles varchar[] NOT NULL DEFAULT '{}';
