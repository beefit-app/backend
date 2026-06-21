package com.beefit.api.service;

import com.beefit.api.dto.AdminDashboardMetricsDTO;
import com.beefit.api.repository.SetRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;


@Service
public class AdminDashboardService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private SetRepository setRepository;

    public AdminDashboardMetricsDTO getConsolidatedMetrics() {

        Long totalUsers = userRepository.count();

        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        Long workouts24h = workoutSessionRepository.countWorkoutsLast24Hours(twentyFourHoursAgo);

        Double totalVolume = setRepository.calculateTotalPlatformVolume();

        return new AdminDashboardMetricsDTO(totalUsers, workouts24h, totalVolume);
    }
}
