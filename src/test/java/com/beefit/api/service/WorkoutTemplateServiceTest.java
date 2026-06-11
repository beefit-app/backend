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

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.dto.WorkoutTemplateExerciseRequestDTO;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;

@ExtendWith(MockitoExtension.class)
class WorkoutTemplateServiceTest {

    @Mock
    private WorkoutTemplateRepository workoutTemplateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CatalogExerciseRepository catalogExerciseRepository;

    @InjectMocks
    private WorkoutTemplateService workoutTemplateService;

    @Test
    void createTemplatePersistsTemplateForUser() {
        UUID userId = UUID.randomUUID();
        UUID catalogExerciseId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setId(catalogExerciseId);
        catalogExercise.setName("Supino");
        WorkoutTemplateRequestDTO request = new WorkoutTemplateRequestDTO(
                "Treino A",
                List.of(MuscleGroups.CHEST),
                List.of(new WorkoutTemplateExerciseRequestDTO(catalogExerciseId, 1)));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(catalogExerciseRepository.findById(catalogExerciseId)).thenReturn(Optional.of(catalogExercise));
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
        assertEquals(1, saved.getExerciseList().size());
        TemplateExercise savedExercise = saved.getExerciseList().getFirst();
        assertEquals(saved, savedExercise.getWorkoutTemplate());
        assertEquals(catalogExercise, savedExercise.getCatalogExercise());
        assertEquals(1, savedExercise.getOrder());
        assertEquals(saved.getId(), response.id());
        assertEquals("Treino A", response.name());
        assertEquals(List.of(MuscleGroups.CHEST), response.muscleGroups());
        assertEquals(1, response.exercises().size());
        assertEquals(catalogExerciseId, response.exercises().getFirst().catalogExerciseId());
        assertEquals("Supino", response.exercises().getFirst().catalogExerciseName());
        assertEquals(1, response.exercises().getFirst().order());
    }

    @Test
    void createTemplateThrowsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        WorkoutTemplateRequestDTO request = new WorkoutTemplateRequestDTO(
                "Treino A",
                List.of(MuscleGroups.CHEST),
                List.of(new WorkoutTemplateExerciseRequestDTO(UUID.randomUUID(), 1)));

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> workoutTemplateService.createTemplate(request, userId));
    }

    @Test
    void getUserTemplatesMapsRepositoryResults() {
        UUID userId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID catalogExerciseId = UUID.randomUUID();
        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setId(catalogExerciseId);
        catalogExercise.setName("Supino");
        WorkoutTemplate template = new WorkoutTemplate();
        template.setId(templateId);
        template.setName("Treino A");
        template.setMuscleGroupsList(List.of(MuscleGroups.CHEST));
        TemplateExercise templateExercise = new TemplateExercise();
        templateExercise.setWorkoutTemplate(template);
        templateExercise.setCatalogExercise(catalogExercise);
        templateExercise.setOrder(1);
        template.setExerciseList(List.of(templateExercise));

        when(workoutTemplateRepository.findByUserId(userId)).thenReturn(List.of(template));

        List<WorkoutTemplateResponseDTO> response = workoutTemplateService.getUserTemplates(userId);

        assertEquals(1, response.size());
        WorkoutTemplateResponseDTO dto = response.getFirst();
        assertEquals(templateId, dto.id());
        assertEquals("Treino A", dto.name());
        assertEquals(List.of(MuscleGroups.CHEST), dto.muscleGroups());
        assertEquals(1, dto.exercises().size());
        assertEquals(catalogExerciseId, dto.exercises().getFirst().catalogExerciseId());
        assertEquals("Supino", dto.exercises().getFirst().catalogExerciseName());
        assertEquals(1, dto.exercises().getFirst().order());
    }
}
