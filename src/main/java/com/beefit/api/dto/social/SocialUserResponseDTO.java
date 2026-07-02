package com.beefit.api.dto.social;

import java.util.UUID;

public record SocialUserResponseDTO(UUID id, String fullName, String username, String profilePicture) {}
