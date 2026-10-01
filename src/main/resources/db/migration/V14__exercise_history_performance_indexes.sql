CREATE INDEX idx_session_exercises_workout_id
    ON public.session_exercises (workout_id);

CREATE INDEX idx_session_exercises_exercise_workout
    ON public.session_exercises (exercise_id, workout_id);

CREATE INDEX idx_sets_exercise_perf
    ON public.sets (exercise_id, weight_kg, reps);

CREATE INDEX idx_workout_sessions_user_status_finished_at
    ON public.workout_sessions (user_id, status, finished_at DESC);
