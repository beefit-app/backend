package com.beefit.api.dto.feed;

import java.util.UUID;

/**
 * Estado autoritativo de uma curtida para atualizar cópias já carregadas do feed.
 * O campo {@code likedByCurrentUser} não é enviado porque varia por destinatário.
 */
public record PostLikeEventDTO(UUID postId, int totalLikes) {
}
