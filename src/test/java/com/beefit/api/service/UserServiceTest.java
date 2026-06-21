package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.beefit.api.domain.User;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.dto.user.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.user.UpdateProfileRequestDTO;
import com.beefit.api.dto.user.UserProfileResponseDTO;
import com.beefit.api.exception.user.InvalidPasswordException;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = user();
    }

    @Test
    void getUserProfileReturnsProfileData() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponseDTO response = userService.getUserProfile(userId);

        assertEquals(user.getFullName(), response.fullName());
        assertEquals(user.getUsername(), response.username());
        assertEquals(user.getBodyWeight(), response.bodyWeight());
        assertEquals(user.getGhostMode(), response.ghostMode());
    }

    @Test
    void getUserProfileThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserProfile(userId));
    }

    @Test
    void updateUserProfileMutatesAllowedFields() {
        UpdateProfileRequestDTO request = new UpdateProfileRequestDTO(
                "New Name",
                "New bio",
                "https://example.com/avatar.png",
                72.5f);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.updateUserProfile(userId, request);

        assertEquals("New Name", user.getFullName());
        assertEquals("New bio", user.getBiography());
        assertEquals("https://example.com/avatar.png", user.getProfilePicture());
        assertEquals(72.5f, user.getBodyWeight());
    }

    @Test
    void changeUserPasswordEncodesNewPasswordWhenCurrentPasswordMatches() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("Newpass1!")).thenReturn("encoded");

        userService.changeUserPassword(userId, new ChangePasswordDTO("current", "Newpass1!"));

        assertEquals("encoded", user.getPasswordHash());
    }

    @Test
    void changeUserPasswordThrowsWhenCurrentPasswordDoesNotMatch() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> userService.changeUserPassword(userId, new ChangePasswordDTO("wrong", "Newpass1!")));
    }

    @Test
    void updateUserGhostModeMutatesSetting() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.updateUserGhostMode(userId, new UpdateAccountSettingsRequestDTO(Boolean.TRUE));

        assertEquals(Boolean.TRUE, user.getGhostMode());
    }

    @Test
    void deleteAccountDeletesWhenPasswordMatches() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", user.getPassword())).thenReturn(true);

        userService.deleteAccount(userId, new DeleteUserRequestDTO("current"));

        verify(userRepository).delete(user);
    }

    @Test
    void deleteAccountThrowsWhenPasswordDoesNotMatch() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> userService.deleteAccount(userId, new DeleteUserRequestDTO("wrong")));
        verify(userRepository, never()).delete(user);
    }

    private User user() {
        User user = new User();
        user.setId(userId);
        user.setFullName("Test User");
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed");
        user.setBiography("bio");
        user.setProfilePicture("picture");
        user.setBodyWeight(80.0f);
        user.setGhostMode(Boolean.FALSE);
        user.setOffensiveHoneys(2);
        return user;
    }
}
