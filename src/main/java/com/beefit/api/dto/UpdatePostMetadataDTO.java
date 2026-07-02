package com.beefit.api.dto;

// Garante que o Admin só altere o que é permitido.
public record UpdatePostMetadataDTO(
        String newTitle,
        String observation
) {}