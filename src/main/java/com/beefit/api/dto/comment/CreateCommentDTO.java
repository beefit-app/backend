package com.beefit.api.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentDTO(
        @NotBlank(message = "Escreva um comentário.")
        @Size(max = 360, message = "O comentário deve ter no máximo 360 caracteres.") String text) {
}
