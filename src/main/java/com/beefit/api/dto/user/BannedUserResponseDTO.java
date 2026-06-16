package com.beefit.api.dto.user;

import java.time.LocalDateTime;
import java.util.UUID;

public record BannedUserResponseDTO (
    UUID userId,
    String username,
    Boolean banned,
    Integer banDays,
    LocalDateTime bannedUntil
){}
