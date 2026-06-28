package com.beefit.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDTO(@NotBlank String refreshToken,
                                     @NotBlank String deviceId) {
}
