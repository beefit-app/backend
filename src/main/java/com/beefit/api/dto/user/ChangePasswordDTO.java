package com.beefit.api.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordDTO(
        @NotBlank String currentPassword,
        @NotBlank(message = "A senha não pode estar em branco.")
        @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres.")
        String newPassword) {
}
