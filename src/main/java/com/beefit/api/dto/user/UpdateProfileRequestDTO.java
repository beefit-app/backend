package com.beefit.api.dto.user;

public record UpdateProfileRequestDTO(
        String fullName,
        String biography,
        String profilePicture,
        Float bodyWeight
) {}