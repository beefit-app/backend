package com.beefit.api.dto.comment;

import java.util.UUID;

// Apenas invalidação: conteúdo e contador são recuperados por REST autorizado.
public record CommentsChangedEventDTO(UUID postId) {
}
