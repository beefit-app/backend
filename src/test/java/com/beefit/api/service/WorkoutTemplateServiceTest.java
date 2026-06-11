package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.mapper.WorkoutTemplateResponseMapper;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;

@ExtendWith(MockitoExtension.class)
class WorkoutTemplateServiceTest {

    @Mock
    private WorkoutTemplateRepository workoutTemplateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkoutTemplateResponseMapper workoutTemplateResponseMapper;

    @InjectMocks
    private WorkoutTemplateService workoutTemplateService;

    @Test
    void createTemplatePersistsTemplateForUser() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        List<TemplateExercise> exercises = List.of(new TemplateExercise());
        WorkoutTemplateRequestDTO request = new WorkoutTemplateRequestDTO(
                "Treino A",
                List.of(MuscleGroups.CHEST),
                exercises);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutTemplateRepository.save(any(WorkoutTemplate.class))).thenAnswer(invocation -> {
            WorkoutTemplate template = invocation.getArgument(0);
            template.setId(UUID.randomUUID());
            return template;
        });

        WorkoutTemplateResponseDTO response = workoutTemplateService.createTemplate(request, userId);

        ArgumentCaptor<WorkoutTemplate> templateCaptor = ArgumentCaptor.forClass(WorkoutTemplate.class);
        org.mockito.Mockito.verify(workoutTemplateRepository).save(templateCaptor.capture());
        WorkoutTemplate saved = templateCaptor.getValue();
        assertEquals("Treino A", saved.getName());
        assertEquals(user, saved.getUser());
        assertEquals(List.of(MuscleGroups.CHEST), saved.getMuscleGroupsList());
        assertEquals(exercises, saved.getExerciseList());
        assertEquals(saved.getId(), response.userID());
    }

    @Test
    void createTemplateThrowsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        WorkoutTemplateRequestDTO request = new WorkoutTemplateRequestDTO(
                "Treino A",
                List.of(MuscleGroups.CHEST),
                List.of(new TemplateExercise()));

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> workoutTemplateService.createTemplate(request, userId));
    }

    @Test
    void getUserTemplatesMapsRepositoryResults() {
        UUID userId = UUID.randomUUID();
        WorkoutTemplate template = new WorkoutTemplate();
        WorkoutTemplateResponseDTO dto = new WorkoutTemplateResponseDTO(
                userId,
                "Treino A",
                List.of(MuscleGroups.CHEST),
                List.of());

        when(workoutTemplateRepository.findByUserId(userId)).thenReturn(List.of(template));
        when(workoutTemplateResponseMapper.toDto(template)).thenReturn(dto);

        List<WorkoutTemplateResponseDTO> response = workoutTemplateService.getUserTemplates(userId);

        assertEquals(List.of(dto), response);
    }
}
