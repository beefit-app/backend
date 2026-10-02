package com.beefit.api.dto.workout;

import java.time.LocalDateTime;

public interface ExerciseHistorySummaryProjection {
    LocalDateTime getFinishedAt();
    Double getWeightKg();
    Integer getReps();
    Long getRnLatest();
    Long getRnBest();
}
