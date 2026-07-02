package com.beefit.api.dto.auth;

public record LoginResponseDTO(String accessToken, String refreshToken, UserResponseDTO user) {
}
