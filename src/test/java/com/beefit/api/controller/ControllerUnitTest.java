package com.beefit.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.dto.AuthenticationDTO;
import com.beefit.api.dto.ChangePasswordDTO;
import com.beefit.api.dto.DeleteUserRequestDTO;
import com.beefit.api.dto.LoginResponseDTO;
import com.beefit.api.dto.StartWorkoutRequestDTO;
import com.beefit.api.dto.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.UpdateProfileRequestDTO;
import com.beefit.api.dto.UserProfileResponseDTO;
import com.beefit.api.dto.UserResponseDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;
import com.beefit.api.dto.WorkoutTemplateExerciseRequestDTO;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.mapper.RegisterMapper;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.UserService;
import com.beefit.api.service.WorkoutSessionService;
import com.beefit.api.service.WorkoutTemplateService;

@ExtendWith(MockitoExtension.class)
class ControllerUnitTest {

    @Mock
    private AuthService authService;

    @Mock
    private RegisterMapper registerMapper;

    @Mock
    private UserResponseMapper userResponseMapper;

    @Mock
    private UserService userService;

    @Mock
    private WorkoutSessionService workoutSessionService;

    @Mock
    private WorkoutTemplateService workoutTemplateService;

    @Mock
    private CatalogExerciseRepository catalogExerciseRepository;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void authLoginReturnsOkWithLoginResponse() {
        AuthController controller = new AuthController(authService, registerMapper, userResponseMapper);
        LoginResponseDTO loginResponse = new LoginResponseDTO(
                "token",
                new UserResponseDTO(UUID.randomUUID(), "Test User", "testuser", "test@example.com", 80.0f, 4));

        when(authService.login("test@example.com", "secret")).thenReturn(loginResponse);

        ResponseEntity<LoginResponseDTO> response = controller.login(new AuthenticationDTO("test@example.com", "secret"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(loginResponse, response.getBody());
    }

    @Test
    void authMeMapsAuthenticatedUser() {
        AuthController controller = new AuthController(authService, registerMapper, userResponseMapper);
        User user = user();
        UserResponseDTO dto = new UserResponseDTO(user.getId(), user.getFullName(), user.getUsername(), user.getEmail(), user.getBodyWeight(), user.getWeeklyGoal());

        when(userResponseMapper.toDto(user)).thenReturn(dto);

        ResponseEntity<UserResponseDTO> response = controller.getAuthUser(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void profileControllerDelegatesMutatingOperationsToService() {
        ProfileController controller = new ProfileController();
        ReflectionTestUtils.setField(controller, "userService", userService);
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
        UserProfileResponseDTO dto = new UserProfileResponseDTO("Test User", "testuser", "bio", "pic", 80.0f, 0, 0, 0, false);

        when(userService.getUserProfile(user.getId())).thenReturn(dto);

        ResponseEntity<UserProfileResponseDTO> response = controller.getProfile(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void workoutStartReturnsCreatedWithLocation() {
        bindRequest("/api/workout/start");
        WorkoutController controller = new WorkoutController(workoutSessionService);
        User user = user();
        UUID workoutId = UUID.randomUUID();
        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(List.of(MuscleGroups.CHEST), Boolean.TRUE, null, List.of());
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
                List.of());

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
                List.of(MuscleGroups.CHEST),
                List.of(new WorkoutTemplateExerciseRequestDTO(catalogExerciseId, 1)));
        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setId(catalogExerciseId);
        catalogExercise.setName("Supino");
        TemplateExercise templateExercise = new TemplateExercise();
        templateExercise.setCatalogExercise(catalogExercise);
        templateExercise.setOrder(1);
        WorkoutTemplateResponseDTO dto = new WorkoutTemplateResponseDTO(
                user.getId(),
                "Treino A",
                List.of(MuscleGroups.CHEST),
                List.of(templateExercise));

        when(workoutTemplateService.createTemplate(request, user.getId())).thenReturn(dto);

        ResponseEntity<WorkoutTemplateResponseDTO> response = controller.createTemplate(request, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void exerciseCatalogControllerListsAndFetchesExercises() {
        ExerciseCatalogController controller = new ExerciseCatalogController(catalogExerciseRepository);
        CatalogExercise exercise = new CatalogExercise();
        UUID exerciseId = UUID.randomUUID();
        exercise.setId(exerciseId);
        exercise.setName("Supino");
        PageRequest pageable = PageRequest.of(0, 20);
        Page<CatalogExercise> page = new PageImpl<>(List.of(exercise), pageable, 1);

        when(catalogExerciseRepository.findByNameContainingIgnoreCase("supino", pageable)).thenReturn(page);
        when(catalogExerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));
        when(catalogExerciseRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000000"))).thenReturn(Optional.empty());

        ResponseEntity<Page<CatalogExercise>> listResponse = controller.listExercises("supino", pageable);
        ResponseEntity<CatalogExercise> foundResponse = controller.getExerciseById(exerciseId);
        ResponseEntity<CatalogExercise> missingResponse = controller.getExerciseById(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertEquals(page, listResponse.getBody());
        assertEquals(HttpStatus.OK, foundResponse.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missingResponse.getStatusCode());
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
