ALTER TABLE card
    ADD COLUMN muscle_intensities jsonb NOT NULL DEFAULT '{}'::jsonb;
