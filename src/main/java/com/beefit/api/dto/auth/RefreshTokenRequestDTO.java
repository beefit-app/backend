package com.beefit.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequestDTO(@NotBlank @Size(max = 512) String refreshToken,
                                     @NotBlank @Size(max = 255) String deviceId) {
}
