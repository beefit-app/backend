package com.beefit.api.dto.hive;

public record HiveRankingDTO(
        java.util.UUID userId,
        String fullName,
        String username,
        String profilePicture,
        Long score // Pode ser a quantidade de treinos ou a soma de volume/poder
) {} 
