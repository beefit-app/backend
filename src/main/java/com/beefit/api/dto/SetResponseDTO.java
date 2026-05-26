package com.beefit.api.dto;

import java.util.UUID;

public record SetResponseDTO(
        UUID id,
        Integer order,
        Double weightKg,
        Integer reps,
        Boolean isPersonalRecord) {
}
