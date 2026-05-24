package com.beefit.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountSettingsRequestDTO(@NotNull Boolean ghostMode) {

}
