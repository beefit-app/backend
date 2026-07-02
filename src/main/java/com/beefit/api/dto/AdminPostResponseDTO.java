package com.beefit.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminPostResponseDTO(
        UUID id,
        UUID userId,
        String username,
        String userFullName,
        String title,
        LocalDateTime publishedAt,
        Integer totalLikes,
        Boolean removed,
        Integer durationSeconds,
        Double totalVolume
) {}
