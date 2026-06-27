package com.beefit.api.dto.friendship;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendRemovedEventDTO(
        UUID friendshipId,
        UUID friendId,
        String friendFullName,
        String friendUsername,
        String friendProfilePicture,
        LocalDateTime removedAt) {
}
