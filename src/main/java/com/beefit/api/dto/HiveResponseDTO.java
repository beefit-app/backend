package com.beefit.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record HiveResponseDTO (
        UUID id,
        String name,
        String inviteCode,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt
) {}
