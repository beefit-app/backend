package com.beefit.api.dto.hive;

public record HiveRankingDTO(
        java.util.UUID userId,
        String fullName,
        String username,
        String profilePicture,
        Boolean developer,
        Long score,
        Integer honeys
) {} 
