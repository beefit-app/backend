package com.beefit.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.YearMonth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.dto.CardFilterDTO;
import com.beefit.api.dto.InventoryCardResponseDTO;
import com.beefit.api.dto.AdminPostResponseDTO;
import com.beefit.api.dto.AdminUserResponseDTO;
import com.beefit.api.dto.UpdatePostMetadataDTO;
import com.beefit.api.dto.auth.AuthenticationDTO;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.dto.auth.LogoutRequestDTO;
import com.beefit.api.dto.auth.RefreshTokenRequestDTO;
import com.beefit.api.dto.auth.RefreshTokenResponseDTO;
import com.beefit.api.dto.auth.UserResponseDTO;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.dto.user.BannedUserResponseDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.dto.user.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.user.UpdateProfileRequestDTO;
import com.beefit.api.dto.user.UserProfileResponseDTO;
import com.beefit.api.exception.workout.InvalidMuscleGroupException;
import com.beefit.api.dto.workout.StartWorkoutRequestDTO;
import com.beefit.api.dto.workout.WorkoutSessionResponseDTO;
import com.beefit.api.dto.workout.WorkoutTemplateExerciseRequestDTO;
import com.beefit.api.dto.workout.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.workout.WorkoutTemplateResponseDTO;
import com.beefit.api.mapper.RegisterMapper;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.service.AdminUserService;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.InventoryService;
import com.beefit.api.service.RateLimitService;
import com.beefit.api.service.UserService;
import com.beefit.api.service.WorkoutModerationService;
import com.beefit.api.service.WorkoutSessionService;
import com.beefit.api.service.WorkoutTemplateService;
import com.beefit.api.service.LegalConsentService;
import com.beefit.api.service.ExerciseCatalogService;
import com.beefit.api.dto.exercise.CatalogExerciseResponseDTO;

@ExtendWith(MockitoExtension.class)
class ControllerUnitTest {

    @Mock
    private AuthService authService;

    @Mock
    private RegisterMapper registerMapper;

    @Mock
    private UserResponseMapper userResponseMapper;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private UserService userService;

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private WorkoutModerationService workoutModerationService;

    @Mock
    private WorkoutSessionService workoutSessionService;

    @Mock
    private WorkoutTemplateService workoutTemplateService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private CatalogExerciseRepository catalogExerciseRepository;

    @Mock
    private LegalConsentService legalConsentService;

    @Mock
    private ExerciseCatalogService exerciseCatalogService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void authLoginReturnsOkWithLoginResponse() {
        AuthController controller = new AuthController(authService, registerMapper, userResponseMapper, rateLimitService, legalConsentService);
        LoginResponseDTO loginResponse = new LoginResponseDTO(
                "token",
                "refresh-token",
                new UserResponseDTO(UUID.randomUUID(), "Test User", "testuser", "test@example.com", 80.0f, 4, com.beefit.api.domain.enums.UserRole.USER));

        when(authService.login("test@example.com", "secret", "device-1")).thenReturn(loginResponse);

        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<LoginResponseDTO> response = controller.login(
                new AuthenticationDTO("test@example.com", "secret", "device-1"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(loginResponse, response.getBody());
    }

    @Test
    void authRefreshReturnsNewTokens() {
        AuthController controller = new AuthController(authService, registerMapper, userResponseMapper, rateLimitService, legalConsentService);
        RefreshTokenResponseDTO refreshResponse = new RefreshTokenResponseDTO("new-access", "new-refresh");

        when(authService.refreshSession("refresh-token", "device-1")).thenReturn(refreshResponse);

        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<RefreshTokenResponseDTO> response = controller.refresh(
                new RefreshTokenRequestDTO("refresh-token", "device-1"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(refreshResponse, response.getBody());
    }

    @Test
    void authLogoutReturnsNoContent() {
        AuthController controller = new AuthController(authService, registerMapper, userResponseMapper, rateLimitService, legalConsentService);

        ResponseEntity<Void> response = controller.logout(new LogoutRequestDTO("refresh-token", "device-1"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(authService).logout("refresh-token", "device-1");
    }

    @Test
    void authMeMapsAuthenticatedUser() {
        AuthController controller = new AuthController(authService, registerMapper, userResponseMapper, rateLimitService, legalConsentService);
        User user = user();
        UserResponseDTO dto = new UserResponseDTO(user.getId(), user.getFullName(), user.getUsername(), user.getEmail(), user.getBodyWeight(), user.getWeeklyGoal(), user.getRole());

        when(userResponseMapper.toDto(user)).thenReturn(dto);

        ResponseEntity<UserResponseDTO> response = controller.getAuthUser(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void profileControllerDelegatesMutatingOperationsToService() {
        ProfileController controller = new ProfileController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "rateLimitService", rateLimitService);
        User user = user();

        ResponseEntity<Void> updateResponse = controller.updateProfile(
                user,
                new UpdateProfileRequestDTO("New Name", "Bio", "pic", 75.0f));
        ResponseEntity<Void> passwordResponse = controller.changePassword(
                user,
                new ChangePasswordDTO("current", "Newpass1!"));
        ResponseEntity<Void> settingsResponse = controller.updateAccountSettings(
                user,
                new UpdateAccountSettingsRequestDTO(Boolean.TRUE));
        ResponseEntity<Void> deleteResponse = controller.deleteAccount(
                user,
                new DeleteUserRequestDTO("current"));

        assertEquals(HttpStatus.NO_CONTENT, updateResponse.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, passwordResponse.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, settingsResponse.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
        verify(userService).updateUserProfile(any(), any());
        verify(userService).changeUserPassword(any(), any());
        verify(userService).updateUserGhostMode(any(), any());
        verify(userService).deleteAccount(any(), any());
    }

    @Test
    void profileGetReturnsServiceResponse() {
        ProfileController controller = new ProfileController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        User user = user();
        UserProfileResponseDTO dto = new UserProfileResponseDTO("Test User", "testuser", "bio", "pic", 80.0f, 0, 0, 0, false, false);

        when(userService.getUserProfile(user.getId())).thenReturn(dto);

        ResponseEntity<UserProfileResponseDTO> response = controller.getProfile(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void profileTrainingDaysPassesAuthenticatedViewerToVisibilityCheck() {
        ProfileController controller = new ProfileController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        User viewer = user();
        when(userService.getPublicTrainingDays(viewer, "friend", YearMonth.of(2026, 8))).thenReturn(java.util.List.of());

        ResponseEntity<List<Integer>> response = controller.getUserTrainingDays(viewer, "friend", "2026-08");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService).getPublicTrainingDays(viewer, "friend", YearMonth.of(2026, 8));
    }

    @Test
    void workoutStartReturnsCreatedWithLocation() {
        bindRequest("/api/workout/start");
        WorkoutController controller = new WorkoutController(workoutSessionService);
        User user = user();
        UUID workoutId = UUID.randomUUID();
        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(List.of("peito"), Boolean.TRUE, null, List.of());
        WorkoutSessionResponseDTO dto = new WorkoutSessionResponseDTO(
                workoutId,
                "in_progress",
                null,
                null,
                null,
                null,
                null,
                List.of("peito"),
                true,
                List.of(),
                null);

        when(workoutSessionService.startWorkout(user.getId(), request)).thenReturn(dto);

        ResponseEntity<WorkoutSessionResponseDTO> response = controller.startWorkout(user, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(dto, response.getBody());
        assertTrue(response.getHeaders().getLocation().toString().endsWith("/api/workout/start/" + workoutId));
    }

    @Test
    void workoutTemplateControllerReturnsOkWithEmptyList() {
        WorkoutTemplateController controller = new WorkoutTemplateController(workoutTemplateService);
        User user = user();

        when(workoutTemplateService.getUserTemplates(user.getId())).thenReturn(List.of());

        ResponseEntity<List<WorkoutTemplateResponseDTO>> response = controller.getUserTemplates(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody());
    }

    @Test
    void workoutTemplateControllerCreatesTemplateForAuthenticatedUser() {
        WorkoutTemplateController controller = new WorkoutTemplateController(workoutTemplateService);
        User user = user();
        UUID catalogExerciseId = UUID.randomUUID();
        WorkoutTemplateRequestDTO request = new WorkoutTemplateRequestDTO(
                "Treino A",
                List.of("peito"),
                List.of(new WorkoutTemplateExerciseRequestDTO(catalogExerciseId, 1)));
        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setId(catalogExerciseId);
        catalogExercise.setName("Supino");
        WorkoutTemplateResponseDTO dto = new WorkoutTemplateResponseDTO(
                user.getId(),
                "Treino A",
                List.of(MuscleGroups.CHEST),
                List.of(new com.beefit.api.dto.workout.WorkoutTemplateExerciseResponseDTO(catalogExerciseId, "Supino", 1)));

        when(workoutTemplateService.createTemplate(request, user.getId())).thenReturn(dto);

        ResponseEntity<WorkoutTemplateResponseDTO> response = controller.createTemplate(request, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void exerciseCatalogControllerListsAndFetchesExercises() {
        ExerciseCatalogController controller = new ExerciseCatalogController(exerciseCatalogService);
        CatalogExercise exercise = new CatalogExercise();
        UUID exerciseId = UUID.randomUUID();
        exercise.setId(exerciseId);
        exercise.setName("Supino");
        PageRequest pageable = PageRequest.of(0, 20);
        CatalogExerciseResponseDTO exerciseDto = new CatalogExerciseResponseDTO(exerciseId, "Supino", null, null, null, null, null, null);
        Page<CatalogExerciseResponseDTO> page = new PageImpl<>(List.of(exerciseDto), pageable, 1);

        when(exerciseCatalogService.list("supino", pageable)).thenReturn(page);
        when(exerciseCatalogService.get(exerciseId)).thenReturn(exerciseDto);

        ResponseEntity<Page<CatalogExerciseResponseDTO>> listResponse = controller.listExercises("supino", pageable);
        ResponseEntity<CatalogExerciseResponseDTO> foundResponse = controller.getExerciseById(exerciseId);

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertEquals(page, listResponse.getBody());
        assertEquals(HttpStatus.OK, foundResponse.getStatusCode());
    }

    @Test
    void inventoryControllerListsCardsWithPortugueseFilters() {
        InventoryController controller = new InventoryController(inventoryService);
        User user = user();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<InventoryCardResponseDTO> page = new PageImpl<>(List.of(), pageable, 0);

        when(inventoryService.getUserCards(any(), any(), any())).thenReturn(page);

        ResponseEntity<Page<InventoryCardResponseDTO>> response = controller.getUserCards(
                user,
                "LENDARIA",
                "PEITO",
                pageable);

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<CardFilterDTO> filtersCaptor = ArgumentCaptor.forClass(CardFilterDTO.class);
        verify(inventoryService).getUserCards(userIdCaptor.capture(), filtersCaptor.capture(), any());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(page, response.getBody());
        assertEquals(user.getId(), userIdCaptor.getValue());
        assertEquals(CardRarity.LEGENDARY, filtersCaptor.getValue().rarity());
        assertEquals(MuscleGroups.CHEST, filtersCaptor.getValue().muscleGroup());
    }

    @Test
    void inventoryControllerAllowsMissingFilters() {
        InventoryController controller = new InventoryController(inventoryService);
        User user = user();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<InventoryCardResponseDTO> page = new PageImpl<>(List.of(), pageable, 0);

        when(inventoryService.getUserCards(any(), any(), any())).thenReturn(page);

        controller.getUserCards(user, null, null, pageable);

        ArgumentCaptor<CardFilterDTO> filtersCaptor = ArgumentCaptor.forClass(CardFilterDTO.class);
        verify(inventoryService).getUserCards(any(), filtersCaptor.capture(), any());
        assertNull(filtersCaptor.getValue().rarity());
        assertNull(filtersCaptor.getValue().muscleGroup());
    }

    @Test
    void inventoryControllerRejectsInvalidMuscleGroup() {
        InventoryController controller = new InventoryController(inventoryService);

        assertThrows(InvalidMuscleGroupException.class,
                () -> controller.getUserCards(user(), "COMMON", "foo", PageRequest.of(0, 20)));
    }

    @Test
    void profileUpdatePassesAuthenticatedUserId() {
        ProfileController controller = new ProfileController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        User user = user();
        UpdateProfileRequestDTO request = new UpdateProfileRequestDTO("New Name", "Bio", "pic", 75.0f);

        controller.updateProfile(user, request);

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(userService).updateUserProfile(userIdCaptor.capture(), any());
        assertEquals(user.getId(), userIdCaptor.getValue());
    }

    @Test
    void adminUserControllerDelegatesSearchAndActions() {
        AdminUserController controller = new AdminUserController(adminUserService, userResponseMapper);
        User user = user();
        AdminUserResponseDTO userDto = new AdminUserResponseDTO(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getBodyWeight(),
                user.getWeeklyGoal(),
                false,
                null);
        BannedUserResponseDTO bannedDto = new BannedUserResponseDTO(user.getId(), user.getUsername(), true, 7, null);
        BannedUserResponseDTO unbannedDto = new BannedUserResponseDTO(user.getId(), user.getUsername(), false, 0, null);
        PageRequest pageable = PageRequest.of(0, 10);
        Page<User> users = new PageImpl<>(List.of(user), pageable, 1);

        when(adminUserService.searchUsers("test", pageable)).thenReturn(users);
        User admin = user();
        admin.setId(UUID.randomUUID());
        admin.setRole(com.beefit.api.domain.enums.UserRole.ADMIN);
        when(adminUserService.banUserForDays(admin.getId(), user.getId(), 7)).thenReturn(bannedDto);
        when(adminUserService.unbanUser(user.getId())).thenReturn(unbannedDto);

        ResponseEntity<Page<AdminUserResponseDTO>> searchResponse = controller.searchUsers("test", pageable);
        ResponseEntity<BannedUserResponseDTO> banResponse = controller.banUser(admin, user.getId(), 7);
        ResponseEntity<BannedUserResponseDTO> unbanResponse = controller.unbanUser(user.getId());

        assertEquals(HttpStatus.OK, searchResponse.getStatusCode());
        assertEquals(List.of(userDto), searchResponse.getBody().getContent());
        assertEquals(bannedDto, banResponse.getBody());
        assertEquals(unbannedDto, unbanResponse.getBody());
    }

    @Test
    void adminWorkoutControllerDelegatesModerationOperations() {
        AdminWorkoutController controller = new AdminWorkoutController(workoutModerationService);
        UUID postId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);
        AdminPostResponseDTO postDto = new AdminPostResponseDTO(
                postId,
                UUID.randomUUID(),
                "testuser",
                "Test User",
                "Treino",
                null,
                3,
                false,
                1800,
                1200.0);
        Page<AdminPostResponseDTO> posts = new PageImpl<>(List.of(postDto), pageable, 1);
        UpdatePostMetadataDTO update = new UpdatePostMetadataDTO("Novo titulo");

        when(workoutModerationService.listAllPosts(pageable)).thenReturn(posts);

        ResponseEntity<Page<AdminPostResponseDTO>> listResponse = controller.listAllPosts(pageable);
        ResponseEntity<Void> editResponse = controller.editPost(postId, update);
        ResponseEntity<Void> deleteResponse = controller.deletePost(postId);

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertEquals(posts, listResponse.getBody());
        assertEquals(HttpStatus.NO_CONTENT, editResponse.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
        verify(workoutModerationService).editPostMetadata(postId, update);
        verify(workoutModerationService).removeInappropriatePost(postId);
    }

    private void bindRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Test User");
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setBodyWeight(80.0f);
        user.setWeeklyGoal(4);
        return user;
    }
}
