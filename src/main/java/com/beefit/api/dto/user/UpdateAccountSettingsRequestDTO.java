package com.beefit.api.dto.user;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountSettingsRequestDTO(@NotNull Boolean ghostMode) {

}
