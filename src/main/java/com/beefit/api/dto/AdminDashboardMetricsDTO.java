package com.beefit.api.dto;

public record AdminDashboardMetricsDTO(
        Long totalActiveUsers,
        Long workoutsLast24Hours,
        Double totalVolumeLifted
) {}
