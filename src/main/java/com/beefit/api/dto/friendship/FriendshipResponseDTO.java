package com.beefit.api.dto.friendship;

import com.beefit.api.dto.social.SocialUserResponseDTO;
import java.time.LocalDateTime;
import java.util.UUID;

public record FriendshipResponseDTO(UUID id, SocialUserResponseDTO user, String status, String direction, LocalDateTime createdAt) {}
