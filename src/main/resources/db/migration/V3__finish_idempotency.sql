ALTER TABLE workout_sessions
    ADD COLUMN finish_idempotency_key varchar(128),
    ADD COLUMN finish_request_hash varchar(64);
