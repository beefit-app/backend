package com.beefit.api.repository;

import com.beefit.api.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByProfilePicture(String profilePicture);

    UserDetails findByEmail(String email);
    UserDetails findByEmailIgnoreCase(String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.profilePicture = :profilePicture " +
            "AND (u.id = :viewerId OR (u.ghostMode = false AND EXISTS (SELECT 1 FROM Friendship f WHERE " +
            "f.status = com.beefit.api.domain.enums.FriendshipStatus.ACCEPTED AND ((f.requester.id = :viewerId AND f.addressee = u) " +
            "OR (f.addressee.id = :viewerId AND f.requester = u)))))")
    boolean existsVisibleProfilePicture(@Param("profilePicture") String profilePicture, @Param("viewerId") UUID viewerId);

    @Modifying
    @Query("UPDATE User u SET u.offensiveHoneys = 0 WHERE (u.weeklyWorkoutsCount + :daysRemaining) < u.weeklyGoal AND u.offensiveHoneys > 0")
    void resetOffensiveForFailedGoals(@Param("daysRemaining") int daysRemaining);

    @Modifying
    @Query("UPDATE User u SET u.weeklyWorkoutsCount = 0")
    void resetAllWeeklyWorkoutsCount();

    @Query("SELECT u FROM User u WHERE u.id <> :currentUserId AND (" +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchSocialUsers(@Param("currentUserId") UUID currentUserId, @Param("search") String search, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> getAllBySearchAndPagination(@Param("search") String search, Pageable pageable);
}
