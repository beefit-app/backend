package com.beefit.api.dto.user;

import java.util.UUID;

public record PublicUserProfileResponseDTO(
        UUID userId,
        String fullName,
        String username,
        String biography,
        String profilePicture,
        Float bodyWeight,
        Integer offensiveHoneys,
        Integer cardsCount,
        Integer totalWorkouts,
        String relationshipStatus,
        UUID friendshipId) {
}
