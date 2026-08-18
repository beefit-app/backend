package com.beefit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Garante que o Admin só altere o que é permitido.
public record UpdatePostMetadataDTO(
        @NotBlank(message = "Informe o novo título.")
        @Size(max = 255, message = "O título deve ter no máximo 255 caracteres.")
        String newTitle
) {}
