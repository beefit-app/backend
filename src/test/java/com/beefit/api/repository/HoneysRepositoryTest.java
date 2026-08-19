package com.beefit.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.beefit.api.domain.User;
import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.support.PostgreSqlTestSupport;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class HoneysRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport
            .createContainer("beefit_honeys_test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }

    @Autowired private UserRepository userRepository;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void eligibleWorkoutEarnsAtMostOneHoneyPerDay() {
        User user = userRepository.saveAndFlush(newUser("daily", 4, 0, 0));
        LocalDateTime dayStart = LocalDateTime.of(2026, 8, 15, 0, 0);
        WorkoutSession currentWorkout = persistFinishedWorkout(user, dayStart.plusHours(10), 1800);
        persistFinishedWorkout(user, dayStart.plusHours(8), 1800, false);
        workoutSessionRepository.flush();

        assertFalse(workoutSessionRepository.existsEligibleFinishedWorkoutOnDay(
                user.getId(), currentWorkout.getId(), WorkoutStatus.FINISHED, 1800,
                dayStart, dayStart.plusDays(1)));

        persistFinishedWorkout(user, dayStart.plusHours(12), 1800);
        workoutSessionRepository.flush();

        assertTrue(workoutSessionRepository.existsEligibleFinishedWorkoutOnDay(
                user.getId(), currentWorkout.getId(), WorkoutStatus.FINISHED, 1800,
                dayStart, dayStart.plusDays(1)));
    }

    @Test
    void unattainableWeeklyGoalResetsOnlyTheFailedStreak() {
        User reachable = userRepository.save(newUser("reachable", 4, 2, 5));
        User unattainable = userRepository.save(newUser("unattainable", 4, 1, 5));
        User completed = userRepository.save(newUser("completed", 4, 4, 5));
        userRepository.flush();

        userRepository.resetOffensiveForFailedGoals(2);
        entityManager.flush();
        entityManager.clear();

        assertEquals(5, userRepository.findById(reachable.getId()).orElseThrow().getOffensiveHoneys());
        assertEquals(0, userRepository.findById(unattainable.getId()).orElseThrow().getOffensiveHoneys());
        assertEquals(5, userRepository.findById(completed.getId()).orElseThrow().getOffensiveHoneys());
    }

    private WorkoutSession persistFinishedWorkout(User user, LocalDateTime finishedAt, int durationSeconds) {
        return persistFinishedWorkout(user, finishedAt, durationSeconds, true);
    }

    private WorkoutSession persistFinishedWorkout(
            User user, LocalDateTime finishedAt, int durationSeconds, boolean hasCompletedSet) {
        WorkoutSession workout = new WorkoutSession();
        workout.setUser(user);
        workout.setStartedAt(finishedAt.minusSeconds(durationSeconds));
        workout.setFinishedAt(finishedAt);
        workout.setDurationSeconds(durationSeconds);
        workout.setStatus(WorkoutStatus.FINISHED);
        workout.setShouldPostWorkout(false);
        workout.setIsPrivate(true);
        workout.setMuscleGroupsList(List.of());

        if (!hasCompletedSet) {
            return workoutSessionRepository.save(workout);
        }

        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setName("Exercício " + UUID.randomUUID());
        catalogExercise.setPrimaryMuscleGroupsList(List.of(MuscleGroups.CHEST));
        catalogExercise.setSecondaryMuscleGroupsList(List.of());
        entityManager.persist(catalogExercise);

        SessionExercise exercise = new SessionExercise();
        exercise.setExercise(catalogExercise);
        exercise.setOrder(1);
        com.beefit.api.domain.Set completedSet = new com.beefit.api.domain.Set();
        completedSet.setOrder(1);
        completedSet.setWeightKg(80.0);
        completedSet.setReps(8);
        completedSet.setIsPersonalRecord(false);
        exercise.addSet(completedSet);
        workout.addExercise(exercise);

        return workoutSessionRepository.save(workout);
    }

    private User newUser(String prefix, int weeklyGoal, int weeklyCount, int honeys) {
        User user = new User();
        user.setFullName("Usuário " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@beefit.test");
        user.setPasswordHash("password-hash");
        user.setBodyWeight(80.0F);
        user.setUsername(prefix + "-" + UUID.randomUUID());
        user.setGhostMode(false);
        user.setWeeklyGoal(weeklyGoal);
        user.setWeeklyWorkoutsCount(weeklyCount);
        user.setWorkoutsCount(0);
        user.setCardsCount(0);
        user.setOffensiveHoneys(honeys);
        user.setRole(UserRole.USER);
        return user;
    }
}
