package com.beefit.api.event.workout;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.annotation.Nullable;

public record WorkoutFinishedEvent(
        UUID workoutSessionId,
        UUID userId,
        LocalDateTime finishedAt,
        int durationSeconds,
        boolean hasCompletedSets,
        String title,
        boolean shouldPostWorkout,
        boolean isPrivate,
        @Nullable UUID photoMediaId
) {
}
