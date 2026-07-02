package com.beefit.api.dto.user;

import jakarta.validation.constraints.*;

public record UpdateProfileRequestDTO(
        @NotBlank @Size(min = 4, max = 100) String fullName,
        @Size(max = 500) String biography,
        String profilePicture,
        @NotNull @Positive @Max(300) Float bodyWeight
) {}