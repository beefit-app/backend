package com.beefit.api.dto.auth;

public record LoginResponseDTO(String token, UserResponseDTO userResponseDTO) {
}
