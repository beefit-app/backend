package com.beefit.api.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.beefit.api.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HoneysScheduleService {
    private final UserRepository userRepository;
    private final Clock businessClock;

    /*
     * RF-011: Executed every day at the 23:59 o'clock, checking if the weekly goal its reachable
     */
    @Scheduled(cron = "0 59 23 * * *", zone = "${beefit.business.time-zone:America/Sao_Paulo}")
    @Transactional
    public void checkWeeklyGoalsAttainability() {
        LocalDate today = LocalDate.now(businessClock);

        int daysRemainingInWeek = 7 - today.getDayOfWeek().getValue();
        userRepository.resetOffensiveForFailedGoals(daysRemainingInWeek);

    }

    /*
     * Executed every monday to set the weekly counter to 0
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "${beefit.business.time-zone:America/Sao_Paulo}")
    @Transactional
    public void resetWeeklyCounters() {

        userRepository.resetAllWeeklyWorkoutsCount();
    }
}
