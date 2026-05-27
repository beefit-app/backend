package com.beefit.api.dto;

import com.beefit.api.domain.enums.SocialEvent;

public record SocialEventDTO(
        SocialEvent type,
        WorkoutPresenceEventDTO workoutPresenceEvent
) {
}
