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

    /**
     * If the goal can't be reached, the offensive counter may reset to 0 if it's already != 0
     */
    @Modifying
    @Query("UPDATE User u SET u.offensiveHoneys = 0 WHERE (u.weeklyWorkoutsCount + :daysRemaining) < u.weeklyGoal AND u.offensiveHoneys > 0")
    void resetOffensiveForFailedGoals(@Param("daysRemaining") int daysRemaining);

    /**
     * The Weekly Counter are setted to 0 every Monday
     */
    @Modifying
    @Query("UPDATE User u SET u.weeklyWorkoutsCount = 0")
    void resetAllWeeklyWorkoutsCount();
}
