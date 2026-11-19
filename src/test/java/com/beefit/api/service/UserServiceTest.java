package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.dto.user.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.user.UpdateProfileRequestDTO;
import com.beefit.api.dto.user.UserProfileResponseDTO;
import com.beefit.api.exception.user.InvalidPasswordException;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.dto.user.PublicUserProfileResponseDTO;
import com.beefit.api.service.FriendshipService.FriendshipRelation;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WorkoutMediaService workoutMediaService;

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private FriendshipService friendshipService;

    @Mock
    private SseService sseService;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private jakarta.persistence.EntityManager entityManager;

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
        when(workoutSessionRepository.countValidFinishedWorkoutsByUserId(
                userId, WorkoutStatus.FINISHED, 1800)).thenReturn(3L);

        UserProfileResponseDTO response = userService.getUserProfile(userId);

        assertEquals(user.getFullName(), response.fullName());
        assertEquals(user.getUsername(), response.username());
        assertEquals(user.getBodyWeight(), response.bodyWeight());
        assertEquals(user.getGhostMode(), response.ghostMode());
        assertEquals(3, response.totalWorkouts());
    }

    @Test
    void getUserProfileMarksDeveloperAccountsWithoutGrantingAdminPrivileges() {
        user.setRole(UserRole.DEVELOPER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.countValidFinishedWorkoutsByUserId(
                userId, WorkoutStatus.FINISHED, 1800)).thenReturn(0L);

        UserProfileResponseDTO response = userService.getUserProfile(userId);

        assertTrue(response.developer());
        assertFalse(user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void getUserProfileThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserProfile(userId));
    }

    @Test
    void getPublicUserProfileUsesTheSameValidWorkoutCountAsTheOwnProfile() {
        User viewer = user();
        User target = user();
        UUID targetId = UUID.randomUUID();
        target.setId(targetId);
        target.setUsername("otheruser");
        when(userRepository.findByUsernameIgnoreCase("otheruser")).thenReturn(Optional.of(target));
        when(friendshipService.getRelationship(viewer, target)).thenReturn(new FriendshipRelation("FRIEND", UUID.randomUUID()));
        when(workoutSessionRepository.countValidFinishedWorkoutsByUserId(
                targetId, WorkoutStatus.FINISHED, 1800)).thenReturn(4L);

        PublicUserProfileResponseDTO response = userService.getPublicUserProfile(viewer, "otheruser");

        assertEquals(4, response.totalWorkouts());
    }

    @Test
    void getPublicTrainingDaysRejectsUsersWithoutAnAcceptedFriendship() {
        User viewer = user();
        User target = user();
        target.setId(UUID.randomUUID());
        target.setUsername("otheruser");
        when(userRepository.findByUsernameIgnoreCase("otheruser")).thenReturn(Optional.of(target));
        when(friendshipService.getRelationship(viewer, target)).thenReturn(new FriendshipRelation("REQUEST_SENT", UUID.randomUUID()));

        assertThrows(UserNotFoundException.class,
                () -> userService.getPublicTrainingDays(viewer, "otheruser", YearMonth.of(2026, 8)));
        verify(workoutSessionRepository, never()).findFinishedDaysByUserAndMonth(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getPublicTrainingDaysAllowsAcceptedFriends() {
        User viewer = user();
        User target = user();
        UUID targetId = UUID.randomUUID();
        target.setId(targetId);
        target.setUsername("otheruser");
        when(userRepository.findByUsernameIgnoreCase("otheruser")).thenReturn(Optional.of(target));
        when(friendshipService.getRelationship(viewer, target)).thenReturn(new FriendshipRelation("FRIEND", UUID.randomUUID()));
        when(workoutSessionRepository.findFinishedDaysByUserAndMonth(
                org.mockito.ArgumentMatchers.eq(targetId), org.mockito.ArgumentMatchers.eq(WorkoutStatus.FINISHED),
                org.mockito.ArgumentMatchers.eq(1800), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());

        assertEquals(java.util.List.of(), userService.getPublicTrainingDays(viewer, "otheruser", YearMonth.of(2026, 8)));
    }

    @Test
    void updateUserProfileMutatesAllowedFields() {
        UpdateProfileRequestDTO request = new UpdateProfileRequestDTO(
                "New Name",
                "New bio",
                "https://example.com/avatar.png",
                72.5f);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(friendshipService.getFriends(userId)).thenReturn(Optional.of(java.util.List.of()));

        userService.updateUserProfile(userId, request);

        assertEquals("New Name", user.getFullName());
        assertEquals("New bio", user.getBiography());
        assertEquals("https://example.com/avatar.png", user.getProfilePicture());
        assertEquals(72.5f, user.getBodyWeight());
        verify(sseService).broadcastToFriends(org.mockito.ArgumentMatchers.any(SocialEventDTO.class),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void updateUserProfileRejectsMediaOwnedByAnotherUser() {
        UUID mediaId = UUID.randomUUID();
        UpdateProfileRequestDTO request = new UpdateProfileRequestDTO(
                "New Name", "New bio", "/api/media/" + mediaId, 72.5f);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Foto de perfil não encontrada."))
                .when(workoutMediaService).consumeForProfile(mediaId, userId);

        assertThrows(IllegalArgumentException.class, () -> userService.updateUserProfile(userId, request));
        assertEquals("picture", user.getProfilePicture());
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
    void changeUserPasswordKeepsSessionsWhenCurrentPasswordIsWrong() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> userService.changeUserPassword(userId, new ChangePasswordDTO("wrong", "Newpass1!")));

        org.mockito.Mockito.verifyNoInteractions(authSessionRepository);
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

        verify(workoutMediaService).deleteAllForOwner(userId);
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
