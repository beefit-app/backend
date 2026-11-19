package com.beefit.api.integration;

import com.beefit.api.domain.Friendship;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.FriendshipStatus;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.dto.user.UpdateAccountSettingsRequestDTO;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.FriendshipRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.SseService;
import com.beefit.api.service.TokenService;
import com.beefit.api.service.UserService;
import com.beefit.api.support.PostgreSqlTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Timeout(30)
class SocialSessionIntegrationTest {
    private static final String PASSWORD = "Test-password-1!";
    private static final String DEVICE = "social-device";
    private static final String PROFILE_UPDATED_FRAME = "event:PROFILE_UPDATED\n";

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport.createContainer("social_session_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }

    @Autowired private AuthService authService;
    @Autowired private SseService sseService;
    @Autowired private TokenService tokenService;
    @Autowired private UserService userService;
    @Autowired private MockMvc mvc;
    @Autowired private AuthSessionRepository authSessions;
    @Autowired private FriendshipRepository friendships;
    @Autowired private WorkoutSessionRepository workouts;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<MvcResult> streams = new ArrayList<>();

    @AfterEach
    void closeStreams() {
        streams.forEach(stream -> {
            if (stream.getRequest().isAsyncStarted()) stream.getRequest().getAsyncContext().complete();
        });
    }

    @Test
    void remoteLogoutClosesOnlyTheStreamOfTheRevokedSession() throws Exception {
        var user = register();
        var firstDevice = login(user, DEVICE);
        var otherDevice = login(user, "other-device");
        var revoked = subscribe(firstDevice);
        var active = subscribe(otherDevice);

        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":"%s","refreshToken":"%s"}
                                """.formatted(DEVICE, firstDevice.refreshToken())))
                .andExpect(status().isNoContent());
        broadcastUpdate(user);

        assertFalse(body(revoked).contains(PROFILE_UPDATED_FRAME));
        assertNull(revoked.getAsyncResult(1_000));
        assertTrue(body(active).contains(PROFILE_UPDATED_FRAME));
    }

    @Test
    void heartbeatClosesAStreamWhenItsSessionExpiresInTheDatabase() throws Exception {
        var user = register();
        var login = login(user, DEVICE);
        var stream = subscribe(login);

        var sessionId = tokenService.validateToken(login.accessToken()).sessionId();
        var session = authSessions.findById(sessionId).orElseThrow();
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        authSessions.saveAndFlush(session);

        sseService.heartbeat();
        assertNull(stream.getAsyncResult(1_000));
        broadcastUpdate(user);
        assertFalse(body(stream).contains(PROFILE_UPDATED_FRAME));
    }

    @Test
    void streamTimeoutIsBoundedByTheValidatedAccessToken() throws Exception {
        var login = login(register(), DEVICE);
        Instant expiresAt = tokenService.validateToken(login.accessToken()).expiresAt();
        long remainingMillis = Duration.between(Instant.now(), expiresAt).toMillis();
        var stream = subscribe(login);

        long timeout = stream.getRequest().getAsyncContext().getTimeout();
        assertTrue(timeout > 0 && timeout <= remainingMillis);
    }

    @Test
    void ghostModeInvalidatesOnlyAcceptedFriendsAfterCommitWithoutIdentifyingData() throws Exception {
        var owner = register();
        var friend = register();
        var pending = register();
        link(friend, owner, FriendshipStatus.ACCEPTED);
        link(owner, pending, FriendshipStatus.PENDING);
        var workout = new WorkoutSession();
        workout.setUser(owner);
        workout.setStartedAt(LocalDateTime.now());
        workout.setStatus(WorkoutStatus.IN_PROGRESS);
        workouts.saveAndFlush(workout);

        var ownerLogin = login(owner, DEVICE);
        var ownerStream = subscribe(ownerLogin);
        var friendStream = subscribe(login(friend, DEVICE));
        var pendingStream = subscribe(login(pending, DEVICE));
        var change = new UpdateAccountSettingsRequestDTO(true);
        var transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            userService.updateUserGhostMode(owner.getId(), change);
            assertEquals(List.of(), socialFrames(friendStream));
            status.setRollbackOnly();
        });
        assertEquals(List.of(), socialFrames(friendStream));

        transaction.executeWithoutResult(status -> {
            userService.updateUserGhostMode(owner.getId(), change);
            assertEquals(List.of(), socialFrames(friendStream));
        });
        var onlyInvalidation = List.of("event:SOCIAL_VISIBILITY_CHANGED\ndata:{}");
        assertEquals(onlyInvalidation, socialFrames(friendStream));
        assertEquals(List.of(), socialFrames(ownerStream));
        assertEquals(List.of(), socialFrames(pendingStream));

        // Salvar o mesmo valor não agenda outra invalidação.
        mvc.perform(put("/api/profile/me/settings")
                        .header("Authorization", "Bearer " + ownerLogin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ghostMode\":true}"))
                .andExpect(status().isNoContent());
        assertEquals(onlyInvalidation, socialFrames(friendStream));
    }

    private void link(User requester, User addressee, FriendshipStatus status) {
        var friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus(status);
        friendships.saveAndFlush(friendship);
    }

    private static List<String> socialFrames(MvcResult stream) {
        return Arrays.stream(body(stream).split("\n\n"))
                .filter(frame -> frame.startsWith("event:") && !frame.startsWith("event:heartbeat\n"))
                .toList();
    }

    private void broadcastUpdate(User user) {
        sseService.broadcastToFriend(new SocialEventDTO<>(SocialEvent.PROFILE_UPDATED, Map.of()), user.getId());
    }

    private MvcResult subscribe(LoginResponseDTO login) throws Exception {
        var stream = mvc.perform(get("/api/social/feed")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted()).andReturn();
        streams.add(stream);
        return stream;
    }

    private static String body(MvcResult stream) {
        return new String(stream.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private User register() throws Exception {
        User user = new User();
        user.setFullName("Social Session Test");
        user.setUsername("social-" + UUID.randomUUID().toString().substring(0, 12));
        user.setEmail(user.getUsername() + "@example.test");
        user.setPasswordHash(PASSWORD);
        user.setBodyWeight(70.0f);
        user.setWeeklyGoal(3);
        authService.register(user);
        return user;
    }

    private LoginResponseDTO login(User user, String device) {
        return authService.login(user.getEmail(), PASSWORD, device);
    }
}
