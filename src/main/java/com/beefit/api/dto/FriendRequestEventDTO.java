package com.beefit.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendRequestEventDTO(
        UUID friendshipId,
        UUID requesterId,
        String requesterUsername,
        LocalDateTime sentAt){
}
