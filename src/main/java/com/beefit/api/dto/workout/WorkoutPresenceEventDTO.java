package com.beefit.api.dto.workout;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WorkoutPresenceEventDTO(
        String eventType,
        UUID workoutId,
        UUID userId,
        String username,
        String fullName,
        String status,
        LocalDateTime occurredAt,
        List<String> muscleGroups) {
}
