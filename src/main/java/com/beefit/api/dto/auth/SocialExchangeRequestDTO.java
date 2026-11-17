package com.beefit.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocialExchangeRequestDTO(@NotBlank @Size(max = 512) String code) {
}
