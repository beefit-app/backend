package com.beefit.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.beefit.api.domain.Card;
import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.Like;
import com.beefit.api.domain.Post;
import com.beefit.api.domain.Report;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.ReportReason;
import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.domain.enums.ReportTargetType;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.repository.CardRepository;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.LikeRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.ReportRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.service.FriendshipService;
import com.beefit.api.service.SseService;
import com.beefit.api.service.WorkoutSessionService;
import com.beefit.api.support.PostgreSqlTestSupport;

// Sem @Transactional de propósito: o bug era de FK no banco, e numa transação de
// teste o DELETE final nunca chega ao PostgreSQL — o teste passaria com o bug.
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WorkoutDeleteIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport
            .createContainer("beefit_workout_delete_test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }

    @Autowired private WorkoutSessionService workoutSessionService;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CatalogExerciseRepository catalogExerciseRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock businessClock;

    @MockitoBean private SseService sseService;
    @MockitoBean private FriendshipService friendshipService;

    @Test
    void deletingFinishedWorkoutRemovesEverythingItGenerated() {
        User owner = newUser();
        owner.setWorkoutsCount(1);
        owner.setCardsCount(1);
        owner = userRepository.saveAndFlush(owner);
        User other = userRepository.saveAndFlush(newUser());
        CatalogExercise exercise = catalogExerciseRepository.saveAndFlush(newExercise());

        WorkoutSession session = workoutSessionRepository.saveAndFlush(finishedWorkout(owner, exercise));

        Inventory inventory = new Inventory();
        inventory.setUser(owner);
        inventory.addCard(newCard(session, exercise));
        inventory = inventoryRepository.saveAndFlush(inventory);
        Card card = inventory.getCards().getFirst();
        session.setCard(card);
        session = workoutSessionRepository.saveAndFlush(session);

        Post post = new Post();
        post.setUser(owner);
        post.setWorkoutSession(session);
        post.setTitle("Treino para apagar");
        post = postRepository.saveAndFlush(post);

        Like like = new Like();
        like.setLikedBy(other);
        like.setPost(post);
        like = likeRepository.saveAndFlush(like);

        Report report = new Report();
        report.setReporter(other);
        report.setTargetType(ReportTargetType.POST);
        report.setTargetPost(post);
        report.setReason(ReportReason.SPAM);
        report.setStatus(ReportStatus.PENDING);
        report = reportRepository.saveAndFlush(report);

        workoutSessionService.deleteWorkout(owner.getId(), session.getId());

        assertTrue(workoutSessionRepository.findById(session.getId()).isEmpty());
        assertTrue(postRepository.findById(post.getId()).isEmpty());
        assertTrue(likeRepository.findById(like.getId()).isEmpty());
        assertTrue(reportRepository.findById(report.getId()).isEmpty());
        assertTrue(cardRepository.findById(card.getId()).isEmpty());
        assertEquals(0, countRows("session_exercises", "workout_id", session.getId()));
        assertEquals(0, inventoryRepository.findById(inventory.getId()).orElseThrow().getTotalCards());

        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        assertEquals(0, reloaded.getWorkoutsCount());
        assertEquals(0, reloaded.getCardsCount());
    }

    @Test
    void deletingWorkoutWithoutPostOrCardRemovesTheSession() {
        User owner = newUser();
        owner.setWorkoutsCount(1);
        owner = userRepository.saveAndFlush(owner);
        CatalogExercise exercise = catalogExerciseRepository.saveAndFlush(newExercise());
        WorkoutSession session = workoutSessionRepository.saveAndFlush(finishedWorkout(owner, exercise));

        workoutSessionService.deleteWorkout(owner.getId(), session.getId());

        assertTrue(workoutSessionRepository.findById(session.getId()).isEmpty());
        assertEquals(0, countRows("session_exercises", "workout_id", session.getId()));
        assertEquals(0, userRepository.findById(owner.getId()).orElseThrow().getWorkoutsCount());
    }

    private int countRows(String table, String column, UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    private User newUser() {
        User user = new User();
        user.setFullName("Usuário Exclusão");
        user.setEmail("delete-" + UUID.randomUUID() + "@beefit.test");
        user.setPasswordHash("password-hash");
        user.setBodyWeight(80.0F);
        user.setUsername("delete-" + UUID.randomUUID());
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

    private WorkoutSession finishedWorkout(User user, CatalogExercise catalogExercise) {
        WorkoutSession workout = new WorkoutSession();
        workout.setUser(user);
        workout.setStartedAt(LocalDateTime.now(businessClock).minusMinutes(45));
        workout.setFinishedAt(LocalDateTime.now(businessClock).minusMinutes(5));
        workout.setMuscleGroupsList(List.of(MuscleGroups.CHEST));
        workout.setShouldPostWorkout(true);
        workout.setIsPrivate(false);
        workout.setStatus(WorkoutStatus.FINISHED);

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

    private Card newCard(WorkoutSession session, CatalogExercise exercise) {
        Card card = new Card();
        card.setWorkoutSession(session);
        card.setExercise(exercise);
        card.setBestSetWeightKg(80.0);
        card.setBestSetReps(8);
        card.setImprovementPercentage(12.0);
        card.setCardRarity(CardRarity.values()[0]);
        card.setMuscleGroup(MuscleGroups.CHEST);
        card.setBodyWeightAtGeneration(80.0);
        card.setMuscleIntensities(Map.of("CHEST", 100));
        return card;
    }
}
