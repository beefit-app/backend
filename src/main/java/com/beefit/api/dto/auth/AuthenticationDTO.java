package com.beefit.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticationDTO(@NotBlank @Email @Size(max = 255) String email,
                                @NotBlank @Size(max = 256) String password,
                                @NotBlank @Size(max = 255) String deviceId) {

}
