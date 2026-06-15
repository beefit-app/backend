package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

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

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.dto.auth.UserResponseDTO;
import com.beefit.api.exception.EmailAlreadyInUse;
import com.beefit.api.exception.UsernameAlreadyInUse;
import com.beefit.api.mapper.UserResponseMapper;
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
    private TokenService tokenService;

    @Mock
    private AuthenticationConfiguration authenticationConfiguration;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void loadUserByUsernameReturnsUserWhenEmailExists() {
        User user = user();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);

        assertSame(user, authService.loadUserByUsername(user.getEmail()));
    }

    @Test
    void loadUserByUsernameThrowsWhenEmailDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(null);

        assertThrows(UsernameNotFoundException.class,
                () -> authService.loadUserByUsername("missing@example.com"));
    }

    @Test
    void loginAuthenticatesAndReturnsTokenWithUserDto() throws Exception {
        User user = user();
        UserResponseDTO dto = new UserResponseDTO(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getBodyWeight(),
                user.getWeeklyGoal());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.getAuthorities());

        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(), "secret")))
                .thenReturn(authentication);
        when(tokenService.generateToken(user)).thenReturn("jwt-token");
        when(userResponseMapper.toDto(user)).thenReturn(dto);

        LoginResponseDTO response = authService.login(user.getEmail(), "secret");

        assertEquals("jwt-token", response.token());
        assertEquals(dto, response.userResponseDTO());
    }

    @Test
    void registerHashesPasswordAndSavesUserWithUserRole() {
        User user = user();
        user.setPasswordHash("plain");

        when(userRepository.findByUsername(user.getUsername())).thenReturn(null);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(null);
        when(passwordEncoder.encode("plain")).thenReturn("hashed");

        authService.register(user);

        assertEquals("hashed", user.getPasswordHash());
        assertEquals(UserRole.USER, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void registerThrowsWhenUsernameAlreadyExists() {
        User user = user();
        when(userRepository.findByUsername(user.getUsername())).thenReturn(user);

        assertThrows(UsernameAlreadyInUse.class, () -> authService.register(user));
    }

    @Test
    void registerThrowsWhenEmailAlreadyExists() {
        User user = user();
        when(userRepository.findByUsername(user.getUsername())).thenReturn(null);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);

        assertThrows(EmailAlreadyInUse.class, () -> authService.register(user));
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
