package com.beefit.api.dto.friendship;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendAcceptedEventDTO (
        UUID friendshipId,
        UUID addresseeId,
        String addresseeUsername,
        LocalDateTime acceptedAt){
}
