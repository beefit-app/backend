package com.beefit.api.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.beefit.api.domain.*;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.exception.PostNotFoundException;
import com.beefit.api.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.dto.workout.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.workout.WorkoutTemplateExerciseRequestDTO;
import com.beefit.api.dto.workout.WorkoutTemplateExerciseResponseDTO;
import com.beefit.api.dto.workout.WorkoutTemplateResponseDTO;
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
    private final PostRepository postRepository;

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
                template.getExerciseList());
    }
    public WorkoutTemplateResponseDTO importTemplateFromPost(UUID postId, UUID currentUserId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(PostNotFoundException::new);

        WorkoutSession session = post.getWorkoutSession();

        List<MuscleGroups> importedMuscleGroups = session.getMuscleGroupsList();

        WorkoutTemplate newTemplate = new WorkoutTemplate();
        newTemplate.setName("Imported from " + post.getUser().getFullName());

        User user = userRepository.findById(currentUserId).orElseThrow(UserNotFoundException::new);
        newTemplate.setUser(user);
        newTemplate.setMuscleGroupsList(importedMuscleGroups);

        List<TemplateExercise> importedExercises = session.getExercises().stream()
                .map(sessionExercise -> {
                    TemplateExercise templateEx = new TemplateExercise();

                    templateEx.setCatalogExercise(sessionExercise.getExercise());

                    templateEx.setWorkoutTemplate(newTemplate);

                    return templateEx;
                })
                .collect(Collectors.toList());

        newTemplate.setExerciseList(importedExercises);

        WorkoutTemplate savedTemplate = WTRepository.save(newTemplate);

        return new WorkoutTemplateResponseDTO(
                savedTemplate.getId(),
                savedTemplate.getName(),
                savedTemplate.getMuscleGroupsList(),
                savedTemplate.getExerciseList()
        );
    }
}
