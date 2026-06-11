package com.beefit.api.repository;

import com.beefit.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    UserDetails findByUsername(String username);

    UserDetails findByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.offensiveHoneys = 0 WHERE (u.weeklyWorkoutsCount + :daysRemaining) < u.weeklyGoal AND u.offensiveHoneys > 0")
    void resetOffensiveForFailedGoals(@Param("daysRemaining") int daysRemaining);

    @Modifying
    @Query("UPDATE User u SET u.weeklyWorkoutsCount = 0")
    void resetAllWeeklyWorkoutsCount();
}
