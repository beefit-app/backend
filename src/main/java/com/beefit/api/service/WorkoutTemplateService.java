package com.beefit.api.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateExerciseRequestDTO;
import com.beefit.api.dto.WorkoutTemplateExerciseResponseDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.exception.CatalogExerciseNotFound;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class WorkoutTemplateService {

    private final WorkoutTemplateRepository WTRepository;
    private final UserRepository userRepository;
    private final CatalogExerciseRepository catalogExerciseRepository;

    @Transactional
    // Lembrar de implementar o id do amigo na parte do social
    public WorkoutTemplateResponseDTO createTemplate(WorkoutTemplateRequestDTO requestDTO, UUID userID /*
                                                                                                        * , UUID
                                                                                                        * friendID
                                                                                                        */) {
        WorkoutTemplate template = new WorkoutTemplate();
        template.setName(requestDTO.name());
        template.setMuscleGroupsList(requestDTO.muscleGroups());

        User user = userRepository.findById(userID)
                .orElseThrow(UserNotFoundException::new);

        /*
         * if(friendID !== null){
         * User friend = userRepository.findById(userID).orElse(null);
         * }
         */

        template.setUser(user);
        template.setExerciseList(buildTemplateExercises(requestDTO.exercises(), template));

        WorkoutTemplate savedTemplate = WTRepository.save(template);

        return toResponse(savedTemplate);
    }

    @Transactional
    public List<WorkoutTemplateResponseDTO> getUserTemplates(UUID userID) {

        List<WorkoutTemplate> templates = WTRepository.findByUserId(userID);

        return templates.stream().map(this::toResponse).toList();
    }

    private List<TemplateExercise> buildTemplateExercises(
            List<WorkoutTemplateExerciseRequestDTO> requestedExercises,
            WorkoutTemplate template) {
        return requestedExercises.stream()
                .map(requestedExercise -> {
                    CatalogExercise catalogExercise = catalogExerciseRepository
                            .findById(requestedExercise.catalogExerciseId())
                            .orElseThrow(CatalogExerciseNotFound::new);

                    TemplateExercise templateExercise = new TemplateExercise();
                    templateExercise.setWorkoutTemplate(template);
                    templateExercise.setCatalogExercise(catalogExercise);
                    templateExercise.setOrder(requestedExercise.order());
                    return templateExercise;
                })
                .toList();
    }

    private WorkoutTemplateResponseDTO toResponse(WorkoutTemplate template) {
        List<WorkoutTemplateExerciseResponseDTO> exercises = template.getExerciseList().stream()
                .map(templateExercise -> new WorkoutTemplateExerciseResponseDTO(
                        templateExercise.getCatalogExercise().getId(),
                        templateExercise.getCatalogExercise().getName(),
                        templateExercise.getOrder()))
                .toList();

        return new WorkoutTemplateResponseDTO(
                template.getId(),
                template.getName(),
                template.getMuscleGroupsList(),
                exercises);
    }

}
