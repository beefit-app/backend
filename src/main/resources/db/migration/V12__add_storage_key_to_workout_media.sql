ALTER TABLE public.workout_media
    ADD COLUMN storage_key varchar(255) NOT NULL,
    DROP COLUMN content;

