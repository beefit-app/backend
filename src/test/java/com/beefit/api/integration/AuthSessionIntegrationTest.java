package com.beefit.api.integration;

import com.beefit.api.domain.User;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.exception.user.InvalidSessionException;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.UserService;
import com.beefit.api.support.PostgreSqlTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Timeout(30)
class AuthSessionIntegrationTest {
    private static final String PASSWORD = "Test-password-1!";
    private static final String DEVICE = "test-device";

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport.createContainer("auth_session_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }

    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void logoutDuringRefreshClosesTheSuccessor() throws Exception {
        var login = login(register(), DEVICE);
        var rotated = overlap(
                () -> authService.refreshSession(login.refreshToken(), DEVICE),
                () -> authService.logout(login.refreshToken(), DEVICE));

        assertThrows(InvalidSessionException.class, () -> authService.refreshSession(rotated.refreshToken(), DEVICE));
    }

    @Test
    void refreshWaitingForLogoutCannotRotate() throws Exception {
        var login = login(register(), DEVICE);
        overlap(() -> {
            authService.logout(login.refreshToken(), DEVICE);
            return null;
        }, () -> assertThrows(InvalidSessionException.class,
                () -> authService.refreshSession(login.refreshToken(), DEVICE)));
    }

    @Test
    void rotatedRefreshNeverWorksAgainAndAncestorLogoutClosesLaterRotations() throws Exception {
        var login = login(register(), DEVICE);
        var next = authService.refreshSession(login.refreshToken(), DEVICE);
        var latest = authService.refreshSession(next.refreshToken(), DEVICE);

        assertThrows(InvalidSessionException.class, () -> authService.refreshSession(login.refreshToken(), DEVICE));
        authService.logout(login.refreshToken(), DEVICE);
        assertThrows(InvalidSessionException.class, () -> authService.refreshSession(latest.refreshToken(), DEVICE));
    }

    @Test
    void logoutFromEarlierLoginDoesNotAffectNewLoginOnTheSameDevice() throws Exception {
        var user = register();
        var earlier = login(user, DEVICE);
        var previous = authService.refreshSession(earlier.refreshToken(), DEVICE);
        var current = login(user, DEVICE);

        assertThrows(InvalidSessionException.class, () -> authService.refreshSession(previous.refreshToken(), DEVICE));
        authService.logout(earlier.refreshToken(), DEVICE);
        authService.logout(current.refreshToken(), "wrong-device");
        assertDoesNotThrow(() -> authService.refreshSession(current.refreshToken(), DEVICE));
    }

    @Test
    void socialLoginDuringRefreshOpensANewFamilyAndClosesThePreviousOne() throws Exception {
        var user = register();
        var login = login(user, DEVICE);
        var socialLogin = new AtomicReference<LoginResponseDTO>();
        var rotated = overlap(
                () -> authService.refreshSession(login.refreshToken(), DEVICE),
                () -> socialLogin.set(authService.loginSocialUser(user, DEVICE)));

        assertThrows(InvalidSessionException.class, () -> authService.refreshSession(rotated.refreshToken(), DEVICE));
        authService.logout(login.refreshToken(), DEVICE);
        assertDoesNotThrow(() -> authService.refreshSession(socialLogin.get().refreshToken(), DEVICE));
    }

    @Test
    void passwordChangeDuringRefreshClosesTheSuccessorAndOtherDevices() throws Exception {
        var user = register();
        var login = login(user, DEVICE);
        var otherDevice = login(user, "other-device");
        var rotated = overlap(
                () -> authService.refreshSession(login.refreshToken(), DEVICE),
                () -> userService.changeUserPassword(user.getId(), new ChangePasswordDTO(PASSWORD, "New-test-password-2!")));

        assertThrows(InvalidSessionException.class, () -> authService.refreshSession(rotated.refreshToken(), DEVICE));
        assertThrows(InvalidSessionException.class, () -> authService.refreshSession(otherDevice.refreshToken(), "other-device"));
    }

    private <T> T overlap(Supplier<T> first, Runnable second) throws Exception {
        CountDownLatch firstFinishedWork = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                T result = first.get();
                status.flush();
                firstFinishedWork.countDown();
                await(commitFirst);
                return result;
            }));
            await(firstFinishedWork);
            var secondResult = executor.submit(() -> {
                secondStarted.countDown();
                second.run();
            });
            await(secondStarted);
            assertThrows(TimeoutException.class, () -> secondResult.get(400, TimeUnit.MILLISECONDS));
            commitFirst.countDown();
            T result = firstResult.get(8, TimeUnit.SECONDS);
            secondResult.get(8, TimeUnit.SECONDS);
            return result;
        } finally {
            commitFirst.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(8, TimeUnit.SECONDS), "A operação concorrente não chegou ao ponto esperado");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private User register() throws Exception {
        User user = new User();
        user.setFullName("Session Integration Test");
        user.setUsername("session-" + UUID.randomUUID().toString().substring(0, 12));
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
