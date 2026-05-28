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
     * RF-011: Executado diariamente às 23:59 para verificar se a meta é atingível
     */
    @Scheduled(cron = "0 59 23 * * *")
    @Transactional
    public void checkWeeklyGoalsAttainability(){
        List<User> users = userRepository.findAll();
        LocalDate today = LocalDate.now();

        int daysRemainingInWeek = 7 - today.getDayOfWeek().getValue();

        for(User u : users){
            if(u.getResetsThisWeek() >= 2) continue;

            int completed = u.getWorkoutsCount();
            int goal = u.getWeeklyGoal();

            if(completed + daysRemainingInWeek < goal){
                u.setOffensiveHoneys(0);
                u.setResetsThisWeek(u.getResetsThisWeek() + 1);
            }
        }
    }

    /*
     * Executado toda Segunda-feira às 00:00 para iniciar uma nova semana
     */
    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void resetWeeklyCounters(){
        List<User> users = userRepository.findAll();

        for(User user : users){
            user.setResetsThisWeek(0);
            user.setOffensiveHoneys(0);

            userRepository.save(user);
        }

    }
}
