package com.beefit.api.dto.workout;

import java.util.UUID;

public interface ExerciseBestScoreProjection {
    UUID getExerciseId();
    Double getBestScore();
}
