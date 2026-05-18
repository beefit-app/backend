package com.beefit.api.dto;

import jakarta.validation.constraints.*;

public record RegisterDTO(@NotBlank String fullName,
                          @NotBlank String username,
                          @NotBlank @Email String email,
                          @NotBlank String password,
                          @NotNull @Positive Float bodyWeight,
                          @NotNull @Min(1) @Max(7) Integer weeklyGoal) {
}
