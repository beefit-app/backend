package com.beefit.api.dto.user;

import jakarta.validation.constraints.NotBlank;

public record DeleteUserRequestDTO(
        @NotBlank(message = "Confirme sua senha.")
        String password
) {
}
