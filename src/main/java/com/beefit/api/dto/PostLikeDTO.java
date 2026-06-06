package com.beefit.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PostLikeDTO(
        @NotNull @Valid Boolean liked,
        @NotNull @PositiveOrZero Integer likes_count) {
}
