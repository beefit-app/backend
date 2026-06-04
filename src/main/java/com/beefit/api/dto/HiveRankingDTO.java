package com.beefit.api.dto;

import com.beefit.api.domain.User;

public record HiveRankingDTO(
        User user,
        Long score // Pode ser a quantidade de treinos ou a soma de volume/poder
) {}