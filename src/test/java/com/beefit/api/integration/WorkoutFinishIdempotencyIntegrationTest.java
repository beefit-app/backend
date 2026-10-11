package com.beefit.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.EntityManager;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.workout.FinishWorkoutExerciseDTO;
import com.beefit.api.dto.workout.FinishWorkoutSetDTO;
import com.beefit.api.dto.workout.FinishWorkoutSnapshotRequestDTO;
import com.beefit.api.exception.workout.IdempotencyConflictException;
import com.beefit.api.repository.CardRepository;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.service.FriendshipService;
import com.beefit.api.service.SseService;
import com.beefit.api.service.WorkoutSessionService;
import com.beefit.api.support.PostgreSqlTestSupport;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WorkoutFinishIdempotencyIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport
            .createContainer("beefit_workout_idempotency_test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }

    @Autowired private WorkoutSessionService workoutSessionService;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CatalogExerciseRepository catalogExerciseRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private EntityManager entityManager;
    // O servico normaliza o finishedAt recebido para o fuso de negocio antes de
    // compara-lo com o startedAt gravado. Montar as datas do teste com o relogio
    // padrao da JVM so funciona em maquina que ja esteja nesse fuso: em runner UTC
    // o fim caia 3h antes do inicio e a validacao recusava.
    @Autowired private Clock businessClock;

    @MockitoBean private SseService sseService;
    @MockitoBean private FriendshipService friendshipService;

    @Test
    void concurrentReplayCreatesExactlyOneSetOfCompletionEffects() throws Exception {
        User user = userRepository.saveAndFlush(newUser());
        CatalogExercise catalogExercise = catalogExerciseRepository.saveAndFlush(newExercise());
        WorkoutSession workout = workoutSessionRepository.saveAndFlush(newWorkout(user, catalogExercise));
        when(friendshipService.getFriends(user.getId())).thenReturn(Optional.of(List.of()));

        String key = "finish-concurrent-1";
        FinishWorkoutSnapshotRequestDTO request = finishRequest(workout, catalogExercise, "Treino de peito");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> finishAfterGate(user.getId(), workout.getId(), key, request, ready, start));
            var second = executor.submit(() -> finishAfterGate(user.getId(), workout.getId(), key, request, ready, start));
            ready.await();
            start.countDown();
            assertEquals(workout.getId(), first.get().id());
            assertEquals(workout.getId(), second.get().id());
        }

        workoutSessionService.finishWorkout(user.getId(), workout.getId(), key, request);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            entityManager.clear();
            User completedUser = userRepository.findById(user.getId()).orElseThrow();
            assertEquals(1, completedUser.getWorkoutsCount());
            assertEquals(0, completedUser.getCardsCount());
            assertEquals(0, cardRepository.count());
            assertEquals(1, postRepository.count());
            assertEquals(Optional.empty(), inventoryRepository.findByUserIdWithCards(user.getId()));
        });

        FinishWorkoutSnapshotRequestDTO changedRequest = finishRequest(workout, catalogExercise, "Conteúdo alterado");
        assertThrows(IdempotencyConflictException.class,
                () -> workoutSessionService.finishWorkout(user.getId(), workout.getId(), key, changedRequest));

        WorkoutSession otherWorkout = newWorkout(user, catalogExercise);
        otherWorkout.setStatus(WorkoutStatus.CANCELED);
        otherWorkout = workoutSessionRepository.saveAndFlush(otherWorkout);
        UUID otherWorkoutId = otherWorkout.getId();
        assertThrows(IdempotencyConflictException.class,
                () -> workoutSessionService.finishWorkout(user.getId(), otherWorkoutId, key, request));
    }

    @Test
    @Transactional
    void finishPersistsExerciseAddedAfterWorkoutStarted() {
        User user = userRepository.saveAndFlush(newUser());
        CatalogExercise catalogExercise = catalogExerciseRepository.saveAndFlush(newExercise());
        WorkoutSession workout = newWorkoutWithoutExercises(user);
        workout = workoutSessionRepository.saveAndFlush(workout);
        when(friendshipService.getFriends(user.getId())).thenReturn(Optional.of(List.of()));

        String finishedAt = agoraNoFusoDeNegocio();
        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                finishedAt,
                List.of("peito"),
                List.of(new FinishWorkoutExerciseDTO(
                        null,
                        catalogExercise.getId().toString(),
                        catalogExercise.getName(),
                        1,
                        List.of(new FinishWorkoutSetDTO(null, 1, 82.5, 8)))),
                "Treino com exercício adicionado",
                false,
                null);

        workoutSessionService.finishWorkout(user.getId(), workout.getId(), "finish-added-exercise", request);

        entityManager.clear();
        WorkoutSession reloaded = workoutSessionRepository.findByIdForFinish(workout.getId()).orElseThrow();
        assertEquals(WorkoutStatus.FINISHED, reloaded.getStatus());
        assertEquals(1, reloaded.getExercises().size());
        SessionExercise persistedExercise = reloaded.getExercises().getFirst();
        assertEquals(catalogExercise.getId(), persistedExercise.getExercise().getId());
        assertEquals(1, persistedExercise.getSets().size());
        assertEquals(82.5, persistedExercise.getSets().getFirst().getWeightKg());
        assertEquals(8, persistedExercise.getSets().getFirst().getReps());
    }

    private com.beefit.api.dto.workout.WorkoutSessionResponseDTO finishAfterGate(
            UUID userId, UUID workoutId, String key, FinishWorkoutSnapshotRequestDTO request,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return workoutSessionService.finishWorkout(userId, workoutId, key, request);
    }

    private User newUser() {
        User user = new User();
        user.setFullName("Usuário Idempotência");
        user.setEmail("idempotency-" + UUID.randomUUID() + "@beefit.test");
        user.setPasswordHash("password-hash");
        user.setBodyWeight(80.0F);
        user.setUsername("idempotency-" + UUID.randomUUID());
        user.setGhostMode(false);
        user.setWeeklyGoal(3);
        user.setWorkoutsCount(0);
        user.setWeeklyWorkoutsCount(0);
        user.setCardsCount(0);
        user.setOffensiveHoneys(0);
        user.setRole(UserRole.USER);
        return user;
    }

    private CatalogExercise newExercise() {
        CatalogExercise exercise = new CatalogExercise();
        exercise.setName("Supino reto");
        exercise.setPrimaryMuscleGroupsList(List.of(MuscleGroups.CHEST));
        exercise.setSecondaryMuscleGroupsList(List.of(MuscleGroups.ARMS));
        exercise.setInstructions("Execute com controle.");
        return exercise;
    }

    private WorkoutSession newWorkout(User user, CatalogExercise catalogExercise) {
        WorkoutSession workout = newWorkoutWithoutExercises(user);

        SessionExercise sessionExercise = new SessionExercise();
        sessionExercise.setExercise(catalogExercise);
        sessionExercise.setOrder(1);
        com.beefit.api.domain.Set set = new com.beefit.api.domain.Set();
        set.setOrder(1);
        set.setWeightKg(80.0);
        set.setReps(8);
        set.setIsPersonalRecord(false);
        sessionExercise.addSet(set);
        workout.addExercise(sessionExercise);
        return workout;
    }

    /**
     * O mesmo instante de agora, com o offset do fuso de negocio. O servico
     * converte o valor recebido para esse fuso antes de comparar com o startedAt,
     * entao montar a data com o fuso da JVM tornava o teste dependente da maquina.
     */
    private String agoraNoFusoDeNegocio() {
        return businessClock.instant().atZone(businessClock.getZone()).toOffsetDateTime().toString();
    }

    private WorkoutSession newWorkoutWithoutExercises(User user) {
        WorkoutSession workout = new WorkoutSession();
        workout.setUser(user);
        workout.setStartedAt(LocalDateTime.now(businessClock).minusMinutes(35));
        workout.setMuscleGroupsList(List.of(MuscleGroups.CHEST));
        workout.setShouldPostWorkout(false);
        workout.setIsPrivate(false);
        workout.setStatus(WorkoutStatus.IN_PROGRESS);
        return workout;
    }

    private FinishWorkoutSnapshotRequestDTO finishRequest(
            WorkoutSession workout, CatalogExercise catalogExercise, String title) {
        SessionExercise exercise = workout.getExercises().getFirst();
        com.beefit.api.domain.Set set = exercise.getSets().getFirst();
        String finishedAt = agoraNoFusoDeNegocio();
        return new FinishWorkoutSnapshotRequestDTO(
                finishedAt,
                List.of("peito"),
                List.of(new FinishWorkoutExerciseDTO(
                        exercise.getId(),
                        catalogExercise.getId().toString(),
                        catalogExercise.getName(),
                        1,
                        List.of(new FinishWorkoutSetDTO(set.getId(), 1, 80.0, 8)))),
                title,
                true,
                null);
    }
}
