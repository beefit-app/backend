package com.beefit.api.dto.social;

import java.util.UUID;

public record ProfileUpdatedEventDTO(
        UUID userId,
        String fullName,
        String username,
        String profilePicture) {
}
