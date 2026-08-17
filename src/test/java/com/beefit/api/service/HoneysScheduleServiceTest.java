package com.beefit.api.service;

import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class HoneysScheduleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock businessClock;

    @InjectMocks
    private HoneysScheduleService honeysScheduleService;

    @Test
    void checksGoalUsingRemainingDaysInBusinessWeek() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-08-19T15:00:00Z"),
                ZoneId.of("America/Sao_Paulo"));
        honeysScheduleService = new HoneysScheduleService(userRepository, fixedClock);

        honeysScheduleService.checkWeeklyGoalsAttainability();

        verify(userRepository).resetOffensiveForFailedGoals(4);
    }

    @Test
    void resetsWeeklyCounters() {
        honeysScheduleService.resetWeeklyCounters();

        verify(userRepository).resetAllWeeklyWorkoutsCount();
    }
}
