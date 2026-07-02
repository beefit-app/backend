package com.beefit.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminUserResponseDTO(
        UUID id,
        String fullName,
        String username,
        String email,
        Float bodyWeight,
        Integer weeklyGoal,
        Boolean banned,
        LocalDateTime bannedUntil
) {}
