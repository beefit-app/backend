package com.beefit.api.dto;

public record UserProfileResponse(
        String fullName,
        String username,
        String biography,
        String profilePicture,
        Integer level,
        Integer offensiveHoneys,
        Integer cardsCount,
        Integer totalWorkouts
) {}
