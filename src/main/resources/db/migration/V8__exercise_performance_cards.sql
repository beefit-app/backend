ALTER TABLE card
    ADD COLUMN exercise_id uuid,
    ADD COLUMN best_set_weight_kg double precision,
    ADD COLUMN best_set_reps integer,
    ADD COLUMN estimated_one_rep_max double precision,
    ADD COLUMN improvement_percentage double precision;

CREATE TEMP TABLE migrated_card_performance AS
WITH valid_sets AS (
    SELECT
        ws.id AS workout_id,
        ws.user_id,
        ws.finished_at,
        se.id AS session_exercise_id,
        se.exercise_id AS catalog_exercise_id,
        se.exercise_order,
        s.weight_kg,
        s.reps,
        s.set_order,
        BOOL_OR(s.weight_kg > 0) OVER (PARTITION BY se.id) AS has_weighted_set
    FROM workout_sessions ws
    JOIN session_exercises se ON se.workout_id = ws.id
    JOIN sets s ON s.exercise_id = se.id
    WHERE ws.status = 'FINISHED'
      AND ws.finished_at IS NOT NULL
      AND s.reps > 0
      AND s.weight_kg >= 0
), ranked_sets AS (
    SELECT
        valid_sets.*,
        CASE
            WHEN has_weighted_set
                THEN weight_kg * (1.0 + LEAST(reps, 10) / 30.0)
            ELSE reps::double precision
        END AS performance_score,
        CASE WHEN has_weighted_set THEN 'WEIGHTED' ELSE 'BODYWEIGHT' END AS metric_type,
        ROW_NUMBER() OVER (
            PARTITION BY session_exercise_id
            ORDER BY
                CASE
                    WHEN has_weighted_set
                        THEN weight_kg * (1.0 + LEAST(reps, 10) / 30.0)
                    ELSE reps::double precision
                END DESC,
                set_order DESC
        ) AS set_rank
    FROM valid_sets
    WHERE NOT has_weighted_set OR weight_kg > 0
), exercise_attempts AS (
    SELECT *
    FROM ranked_sets
    WHERE set_rank = 1
), attempts_with_history AS (
    SELECT
        exercise_attempts.*,
        MAX(performance_score) OVER (
            PARTITION BY user_id, catalog_exercise_id, metric_type
            ORDER BY finished_at, workout_id
            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
        ) AS previous_best
    FROM exercise_attempts
), extraordinary_attempts AS (
    SELECT
        attempts_with_history.*,
        ((performance_score - previous_best) / previous_best) * 100.0 AS improvement
    FROM attempts_with_history
    WHERE previous_best > 0
      AND ((performance_score - previous_best) / previous_best) * 100.0 >= 3.0
), ranked_workout_candidates AS (
    SELECT
        extraordinary_attempts.*,
        ROW_NUMBER() OVER (
            PARTITION BY workout_id
            ORDER BY improvement DESC, performance_score DESC, exercise_order ASC
        ) AS workout_rank
    FROM extraordinary_attempts
)
SELECT
    workout_id,
    catalog_exercise_id,
    weight_kg,
    reps,
    CASE WHEN metric_type = 'WEIGHTED' THEN performance_score ELSE NULL END AS estimated_one_rep_max,
    improvement
FROM ranked_workout_candidates
WHERE workout_rank = 1;

UPDATE card c
SET exercise_id = migrated.catalog_exercise_id,
    best_set_weight_kg = migrated.weight_kg,
    best_set_reps = migrated.reps,
    estimated_one_rep_max = migrated.estimated_one_rep_max,
    improvement_percentage = migrated.improvement,
    card_rarity = CASE
        WHEN migrated.improvement >= 25.0 THEN 'LEGENDARY'
        WHEN migrated.improvement >= 15.0 THEN 'EPIC'
        WHEN migrated.improvement >= 7.5 THEN 'RARE'
        ELSE 'COMMON'
    END
FROM migrated_card_performance migrated
WHERE migrated.workout_id = c.workout_session_id;

UPDATE card c
SET muscle_group = COALESCE(e.primary_muscle_groups[1], c.muscle_group),
    muscle_intensities = COALESCE(
        (
            SELECT jsonb_object_agg(muscle_slug, intensity)
            FROM (
                SELECT LOWER(muscle) AS muscle_slug, MAX(intensity) AS intensity
                FROM (
                    SELECT UNNEST(e.primary_muscles)::text AS muscle, 4 AS intensity
                    UNION ALL
                    SELECT UNNEST(e.secondary_muscles)::text AS muscle, 2 AS intensity
                ) exercise_muscles
                GROUP BY LOWER(muscle)
            ) normalized_muscles
        ),
        c.muscle_intensities
    )
FROM exercises e
WHERE e.id = c.exercise_id;

UPDATE workout_sessions ws
SET card_id = NULL
FROM card c
WHERE ws.card_id = c.id
  AND c.exercise_id IS NULL;

DELETE FROM card
WHERE exercise_id IS NULL;

UPDATE inventory i
SET total_cards = (
    SELECT COUNT(*)
    FROM card c
    WHERE c.inventory_id = i.id
);

UPDATE users u
SET cards_count = (
    SELECT COUNT(*)
    FROM card c
    JOIN inventory i ON i.id = c.inventory_id
    WHERE i.user_id = u.id
);

ALTER TABLE card
    ALTER COLUMN exercise_id SET NOT NULL,
    ALTER COLUMN best_set_weight_kg SET NOT NULL,
    ALTER COLUMN best_set_reps SET NOT NULL,
    ALTER COLUMN improvement_percentage SET NOT NULL,
    ADD CONSTRAINT fk_card_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id),
    DROP COLUMN attack_power,
    DROP COLUMN is_personal_record;

CREATE INDEX idx_card_exercise_id ON card(exercise_id);

DROP TABLE migrated_card_performance;
