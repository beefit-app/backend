package com.beefit.api.dto.friendship;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendRequestEventDTO(
        UUID friendshipId,
        UUID requesterId,
        String requesterFullName,
        String requesterUsername,
        String requesterProfilePicture,
        LocalDateTime sentAt){
}
