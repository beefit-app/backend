package com.beefit.api.dto.auth;

import java.util.UUID;

public record UserResponseDTO(UUID id,
                              String fullName,
                              String username,
                              String email,
                              Float bodyWeight,
                              Integer weeklyGoal) {
}
