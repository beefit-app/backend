package com.beefit.api.dto;

public record UpdateProfileRequestDTO(
        String fullName,
        String biography,
        String profilePicture,
        Float bodyWeight
) {}