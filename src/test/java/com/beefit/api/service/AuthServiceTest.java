package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.beefit.api.domain.AuthSession;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.dto.auth.RefreshTokenResponseDTO;
import com.beefit.api.dto.auth.UserResponseDTO;
import com.beefit.api.exception.user.EmailAlreadyInUse;
import com.beefit.api.exception.user.InvalidSessionException;
import com.beefit.api.exception.user.UsernameAlreadyInUse;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserResponseMapper userResponseMapper;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthenticationConfiguration authenticationConfiguration;

    @Mock
    private AuthenticationManager authenticationManager;

    // Instância real (beta desligado por padrão); os testes de beta ligam via reflexão.
    @org.mockito.Spy
    private RegistrationPolicy registrationPolicy = new RegistrationPolicy();

    @InjectMocks
    private AuthService authService;

    @Test
    void loadUserByUsernameReturnsUserWhenEmailExists() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(user);

        assertSame(user, authService.loadUserByUsername(user.getEmail()));
    }

    @Test
    void loadUserByUsernameThrowsWhenEmailDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(null);

        assertThrows(UsernameNotFoundException.class,
                () -> authService.loadUserByUsername("missing@example.com"));
    }

    @Test
    void loginAuthenticatesAndReturnsSessionWithUserDto() throws Exception {
        User user = user();
        UserResponseDTO dto = new UserResponseDTO(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getBodyWeight(),
                user.getWeeklyGoal(),
                user.getRole(),
                user.isProfileComplete());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.getAuthorities());

        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(), "secret")))
                .thenReturn(authentication);
        when(authSessionRepository.save(org.mockito.ArgumentMatchers.any(AuthSession.class)))
                .thenAnswer(invocation -> {
                    AuthSession saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(UUID.randomUUID());
                    return saved;
                });
        when(tokenService.generateToken(org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn("jwt-token");
        when(userResponseMapper.toDto(user)).thenReturn(dto);
        when(authSessionRepository.findAllByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq(user.getId()),
                org.mockito.ArgumentMatchers.eq("device-1"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(java.util.List.of());

        LoginResponseDTO response = authService.login(user.getEmail(), "secret", "device-1");

        assertEquals("jwt-token", response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals(dto, response.user());
    }

    @Test
    void registerHashesPasswordAndSavesUserWithUserRole() {
        User user = user();
        user.setPasswordHash("plain");

        when(userRepository.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(null);
        when(passwordEncoder.encode("plain")).thenReturn("hashed");

        authService.register(user);

        assertEquals("hashed", user.getPasswordHash());
        assertEquals(UserRole.USER, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void registerCanonicalizesEmailAndUsernameBeforeCheckingAndSaving() {
        User user = user();
        user.setEmail("  Test@Example.COM ");
        user.setUsername("  TestUser ");
        user.setPasswordHash("plain");
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(null);
        when(passwordEncoder.encode("plain")).thenReturn("hashed");

        authService.register(user);

        assertEquals("test@example.com", user.getEmail());
        assertEquals("testuser", user.getUsername());
        verify(userRepository).save(user);
    }

    @Test
    void registerThrowsWhenUsernameAlreadyExists() {
        User user = user();
        when(userRepository.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));

        assertThrows(UsernameAlreadyInUse.class, () -> authService.register(user));
    }

    @Test
    void registerThrowsWhenEmailAlreadyExists() {
        User user = user();
        when(userRepository.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(user);

        assertThrows(EmailAlreadyInUse.class, () -> authService.register(user));
    }

    @Test
    void refreshSessionRotatesRefreshToken() {
        User user = user();
        AuthSession currentSession = new AuthSession();
        currentSession.setUser(user);
        currentSession.setDeviceId("device-1");
        currentSession.setExpiresAt(LocalDateTime.now().plusDays(1));
        currentSession.setLastUsedAt(LocalDateTime.now().minusHours(1));

        when(authSessionRepository.findByRefreshTokenHashAndDeviceIdAndRevokedAtIsNull(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("device-1")))
                .thenReturn(Optional.of(currentSession));
        when(authSessionRepository.save(org.mockito.ArgumentMatchers.any(AuthSession.class)))
                .thenAnswer(invocation -> {
                    AuthSession saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(UUID.randomUUID());
                    return saved;
                });
        when(tokenService.generateToken(org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn("new-access-token");

        RefreshTokenResponseDTO response = authService.refreshSession("refresh-token", "device-1");

        assertEquals("new-access-token", response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotNull(currentSession.getRevokedAt());
    }

    @Test
    void refreshSessionRejectsExpiredSession() {
        User user = user();
        AuthSession currentSession = new AuthSession();
        currentSession.setUser(user);
        currentSession.setDeviceId("device-1");
        currentSession.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(authSessionRepository.findByRefreshTokenHashAndDeviceIdAndRevokedAtIsNull(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("device-1")))
                .thenReturn(Optional.of(currentSession));

        assertThrows(InvalidSessionException.class, () -> authService.refreshSession("refresh-token", "device-1"));
    }

    @Test
    void logoutRevokesCurrentSessionWhenPresent() {
        AuthSession currentSession = new AuthSession();
        currentSession.setDeviceId("device-1");
        currentSession.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(authSessionRepository.findByRefreshTokenHashAndDeviceIdAndRevokedAtIsNull(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("device-1")))
                .thenReturn(Optional.of(currentSession));

        authService.logout("refresh-token", "device-1");

        assertNotNull(currentSession.getRevokedAt());
        assertNotNull(currentSession.getLastUsedAt());
    }

    @Test
    void registerThrowsWhenBetaEnabledAndEmailNotInAllowlist() {
        org.springframework.test.util.ReflectionTestUtils.setField(registrationPolicy, "betaEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(registrationPolicy, "allowedEmails", new String[]{"allowed@example.com"});

        User user = user();
        user.setEmail("unauthorized@example.com");

        var ex = assertThrows(com.beefit.api.exception.user.RegistrationRestrictedException.class,
                () -> authService.register(user));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("restrito"));
    }

    @Test
    void registerSucceedsWhenBetaEnabledAndEmailInAllowlist() {
        org.springframework.test.util.ReflectionTestUtils.setField(registrationPolicy, "betaEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(registrationPolicy, "allowedEmails", new String[]{"allowed@example.com"});

        User user = user();
        user.setEmail("ALLOWED@EXAMPLE.COM");

        when(userRepository.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("allowed@example.com")).thenReturn(null);
        when(passwordEncoder.encode(user.getPasswordHash())).thenReturn("encoded-password");
        when(userRepository.save(user)).thenReturn(user);

        authService.register(user);

        verify(userRepository).save(user);
        verify(inventoryRepository).save(org.mockito.ArgumentMatchers.any(Inventory.class));
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Test User");
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPasswordHash("secret");
        user.setBodyWeight(80.0f);
        user.setWeeklyGoal(4);
        user.setRole(UserRole.USER);
        return user;
    }
}

