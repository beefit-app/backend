package com.beefit.api.dto;

public record UpdateProfileRequest(
        String fullName,
        String biography,
        String profilePicture,
        Float bodyWeight
) {}