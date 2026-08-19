package com.beefit.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.beefit.api.domain.Friendship;
import com.beefit.api.domain.Hive;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.FriendshipStatus;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.hive.HiveRankingDTO;
import com.beefit.api.support.PostgreSqlTestSupport;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SocialVisibilityAndHiveRankingRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport
            .createContainer("beefit_social_hive_test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private HiveRepository hiveRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void liveWorkoutsIncludeOnlyAcceptedFriendsOutsideGhostMode() {
        User viewer = persistUser("viewer", false);
        User acceptedFriend = persistUser("accepted", false);
        User privateFriend = persistUser("private", false);
        User pendingFriend = persistUser("pending", false);
        User ghostFriend = persistUser("ghost", true);
        User stranger = persistUser("stranger", false);

        persistFriendship(viewer, acceptedFriend, FriendshipStatus.ACCEPTED);
        persistFriendship(viewer, privateFriend, FriendshipStatus.ACCEPTED);
        persistFriendship(viewer, pendingFriend, FriendshipStatus.PENDING);
        persistFriendship(ghostFriend, viewer, FriendshipStatus.ACCEPTED);

        WorkoutSession visible = persistWorkout(acceptedFriend, WorkoutStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 8, 15, 10, 0), false);
        persistWorkout(pendingFriend, WorkoutStatus.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 11, 0), false);
        persistWorkout(ghostFriend, WorkoutStatus.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 12, 0), false);
        persistWorkout(stranger, WorkoutStatus.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 13, 0), false);
        persistWorkout(privateFriend, WorkoutStatus.IN_PROGRESS, LocalDateTime.of(2026, 8, 15, 14, 0), true);
        entityManager.flush();
        entityManager.clear();

        List<WorkoutSession> result = workoutSessionRepository
                .findAllVisibleToFriendByStatusExcludingUser(WorkoutStatus.IN_PROGRESS, viewer.getId());

        assertEquals(List.of(visible.getId()), result.stream().map(WorkoutSession::getId).toList());
    }

    @Test
    void hiveRankingCountsOnlyFinishedWorkoutsThroughTheWholeEndDate() {
        User member = persistUser("member", false);
        Hive hive = new Hive();
        hive.setName("Test Hive");
        hive.setInviteCode("HIVE-" + UUID.randomUUID());
        hive.setStartDate(LocalDate.of(2026, 8, 1));
        hive.setEndDate(LocalDate.of(2026, 8, 7));
        hive.setCreator(member);
        hive.addMember(member);
        entityManager.persist(hive);

        persistWorkout(member, WorkoutStatus.FINISHED, LocalDateTime.of(2026, 8, 1, 0, 0), false);
        persistWorkout(member, WorkoutStatus.FINISHED, LocalDateTime.of(2026, 8, 7, 23, 59, 59), false);
        persistWorkout(member, WorkoutStatus.FINISHED, LocalDateTime.of(2026, 8, 8, 0, 0), false);
        persistWorkout(member, WorkoutStatus.IN_PROGRESS, LocalDateTime.of(2026, 8, 3, 12, 0), false);
        entityManager.flush();
        entityManager.clear();

        List<HiveRankingDTO> ranking = hiveRepository.getRankingForHive(
                hive.getId(),
                WorkoutStatus.FINISHED,
                hive.getStartDate().atStartOfDay(),
                hive.getEndDate().plusDays(1).atStartOfDay());

        assertEquals(1, ranking.size());
        assertEquals(member.getId(), ranking.getFirst().userId());
        assertEquals(2L, ranking.getFirst().score());
    }

    private User persistUser(String name, boolean ghostMode) {
        User user = new User();
        user.setFullName(name + " User");
        user.setEmail(name + "-" + UUID.randomUUID() + "@beefit.test");
        user.setPasswordHash("password-hash");
        user.setBodyWeight(80.0F);
        user.setUsername(name + "-" + UUID.randomUUID());
        user.setGhostMode(ghostMode);
        user.setWeeklyGoal(3);
        user.setWorkoutsCount(0);
        user.setWeeklyWorkoutsCount(0);
        user.setCardsCount(0);
        user.setOffensiveHoneys(0);
        user.setRole(UserRole.USER);
        entityManager.persist(user);
        return user;
    }

    private void persistFriendship(User requester, User addressee, FriendshipStatus status) {
        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus(status);
        entityManager.persist(friendship);
    }

    private WorkoutSession persistWorkout(
            User user,
            WorkoutStatus status,
            LocalDateTime startedAt,
            boolean isPrivate) {
        WorkoutSession workout = new WorkoutSession();
        workout.setUser(user);
        workout.setStartedAt(startedAt);
        workout.setFinishedAt(status == WorkoutStatus.FINISHED ? startedAt.plusHours(1) : null);
        workout.setStatus(status);
        workout.setShouldPostWorkout(false);
        workout.setIsPrivate(isPrivate);
        entityManager.persist(workout);
        return workout;
    }
}
