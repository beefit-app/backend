package com.beefit.api.dto;

public record UserProfileResponseDTO(
        String fullName,
        String username,
        String biography,
        String profilePicture,
        Float bodyWeight,
        Integer offensiveHoneys,
        Integer cardsCount,
        Integer totalWorkouts,
        Boolean ghostMode) {
}
