package com.beefit.api.dto.auth;

import java.util.UUID;
import com.beefit.api.domain.enums.UserRole;


public record UserResponseDTO(UUID id,
                              String fullName,
                              String username,
                              String email,
                              Float bodyWeight,
                              Integer weeklyGoal,
                              UserRole role,
                              boolean profileComplete) {
}
