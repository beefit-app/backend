package com.beefit.api.dto.auth;

public record RefreshTokenResponseDTO(String accessToken, String refreshToken) {
}
