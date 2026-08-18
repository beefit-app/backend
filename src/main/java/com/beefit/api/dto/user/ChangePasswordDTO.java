package com.beefit.api.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordDTO(
        @NotBlank
        @Size(max = 256, message = "A senha atual deve ter no máximo 256 caracteres.")
        String currentPassword,
        @NotBlank(message = "A senha não pode estar em branco.")
        @Size(min = 8, max = 64, message = "A senha deve ter de 8 a 64 caracteres.")
        @Pattern(
                regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
                message = "A senha deve incluir pelo menos uma letra e um número.")
        String newPassword) {
}
