ALTER TABLE workout_sessions
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT workout_sessions_finish_idempotency_pair_check
        CHECK (
            (finish_idempotency_key IS NULL AND finish_request_hash IS NULL)
            OR (finish_idempotency_key IS NOT NULL AND finish_request_hash IS NOT NULL)
        );

CREATE UNIQUE INDEX workout_sessions_user_finish_idempotency_key
    ON workout_sessions (user_id, finish_idempotency_key)
    WHERE finish_idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX workout_sessions_one_active_per_user
    ON workout_sessions (user_id)
    WHERE status = 'IN_PROGRESS';
