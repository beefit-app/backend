package com.beefit.api.dto.social;

import java.time.LocalDateTime;
import java.util.UUID;

public record DataSocialEventDTO(
        UUID userId,
        String username,
        LocalDateTime timestamp
) {
}
