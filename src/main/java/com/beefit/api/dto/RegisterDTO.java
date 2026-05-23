package com.beefit.api.dto;

import jakarta.validation.constraints.*;

public record RegisterDTO(@NotBlank String fullName,
                          @NotBlank String username,
                          @NotBlank @Email String email,
                          @Pattern(
                                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$",
                                message = "A senha deve ter no mínimo 8 caracteres, incluindo 1 maiúscula, 1 minúscula, 1 número e 1 caractere especial."
                          )
                          @NotBlank
                          String password,
                          @NotNull @Positive Float bodyWeight,
                          @NotNull @Min(1) @Max(7) Integer weeklyGoal) {
}
