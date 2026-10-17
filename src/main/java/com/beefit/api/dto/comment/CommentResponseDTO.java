package com.beefit.api.dto.comment;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommentResponseDTO(UUID id, UUID authorId, String username, String avatarUrl,
                                 boolean developer, String text, LocalDateTime createdAt,
                                 boolean canDelete, boolean canReport) {
}
