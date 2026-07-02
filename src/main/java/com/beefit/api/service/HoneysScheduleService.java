package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class HoneysScheduleService {
    @Autowired
    private UserRepository userRepository;

    /*
     * RF-011: Executed every day at the 23:59 o'clock, checking if the weekly goal its reachable
     */
    @Scheduled(cron = "0 59 23 * * *")
    @Transactional
    public void checkWeeklyGoalsAttainability(){
        LocalDate today = LocalDate.now();

        int daysRemainingInWeek = 7 - today.getDayOfWeek().getValue();
        userRepository.resetOffensiveForFailedGoals(daysRemainingInWeek);

    }

    /*
     * Executed every monday to set the weekly counter to 0
     */
    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void resetWeeklyCounters(){

        userRepository.resetAllWeeklyWorkoutsCount();
    }
}
