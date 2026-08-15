package com.beefit.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.beefit.api.domain.AuthSession;
import com.beefit.api.domain.Friendship;
import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.FriendshipStatus;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.exception.user.InvalidSessionException;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.FriendshipRepository;
import com.beefit.api.repository.LikeRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.PostService;
import com.beefit.api.support.PostgreSqlTestSupport;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentMutationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport
            .createContainer("beefit_concurrent_mutation_test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }

    @Autowired private AuthService authService;
    @Autowired private PostService postService;
    @Autowired private AuthSessionRepository authSessionRepository;
    @Autowired private FriendshipRepository friendshipRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;

    @Test
    void concurrentRefreshRotatesTokenOnlyOnce() throws Exception {
        User user = userRepository.saveAndFlush(newUser("refresh"));
        String refreshToken = "refresh-token-" + UUID.randomUUID();
        String deviceId = "device-1";

        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setRefreshTokenHash(sha256(refreshToken));
        session.setDeviceId(deviceId);
        session.setCreatedAt(LocalDateTime.now());
        session.setLastUsedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(30));
        authSessionRepository.saveAndFlush(session);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                attempts.add(executor.submit(() -> refreshAfterGate(refreshToken, deviceId, ready, start)));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) successes++;
            }
            assertEquals(1, successes);
        }

        assertEquals(1, authSessionRepository
                .findAllByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(
                        user.getId(), deviceId, LocalDateTime.now())
                .size());
    }

    @Test
    void concurrentLikesKeepCounterConsistentWithRows() throws Exception {
        User owner = userRepository.saveAndFlush(newUser("owner"));
        WorkoutSession workout = new WorkoutSession();
        workout.setUser(owner);
        workout.setStartedAt(LocalDateTime.now().minusHours(1));
        workout.setFinishedAt(LocalDateTime.now());
        workout.setDurationSeconds(3600);
        workout.setEstimatedCalories(500.0);
        workout.setTotalVolume(1000.0);
        workout.setMuscleGroupsList(List.of());
        workout.setShouldPostWorkout(true);
        workout.setIsPrivate(false);
        workout.setStatus(WorkoutStatus.FINISHED);
        workout = workoutSessionRepository.saveAndFlush(workout);

        Post post = new Post();
        post.setUser(owner);
        post.setWorkoutSession(workout);
        post.setTotalLikes(0);
        post.setIsRemoved(false);
        post = postRepository.saveAndFlush(post);

        int likerCount = 6;
        List<User> likers = new ArrayList<>();
        for (int index = 0; index < likerCount; index++) {
            User liker = userRepository.saveAndFlush(newUser("liker-" + index));
            likers.add(liker);

            Friendship friendship = new Friendship();
            friendship.setRequester(owner);
            friendship.setAddressee(liker);
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            friendshipRepository.save(friendship);
        }
        friendshipRepository.flush();

        UUID postId = post.getId();
        CountDownLatch ready = new CountDownLatch(likerCount);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(likerCount)) {
            List<Future<Void>> likes = new ArrayList<>();
            for (User liker : likers) {
                likes.add(executor.submit(() -> {
                    likeAfterGate(liker.getId(), postId, ready, start);
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> like : likes) like.get();
        }

        Post updatedPost = postRepository.findById(postId).orElseThrow();
        assertEquals(likerCount, updatedPost.getTotalLikes());
        assertEquals(likerCount, likeRepository.countByPost(updatedPost));
    }

    private boolean refreshAfterGate(String refreshToken, String deviceId,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            authService.refreshSession(refreshToken, deviceId);
            return true;
        } catch (InvalidSessionException expected) {
            return false;
        }
    }

    private void likeAfterGate(UUID userId, UUID postId,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        postService.toggleLike(userId, postId);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setFullName("Usuário " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@beefit.test");
        user.setPasswordHash("password-hash");
        user.setBodyWeight(80.0F);
        user.setUsername(prefix + "-" + UUID.randomUUID());
        user.setGhostMode(false);
        user.setWeeklyGoal(3);
        user.setWorkoutsCount(0);
        user.setWeeklyWorkoutsCount(0);
        user.setCardsCount(0);
        user.setOffensiveHoneys(0);
        user.setRole(UserRole.USER);
        return user;
    }
}
