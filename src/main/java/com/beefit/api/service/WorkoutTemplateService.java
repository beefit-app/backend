package com.beefit.api.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.ArrayList;

import com.beefit.api.domain.*;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.exception.friendship.PostNotFoundException;
import com.beefit.api.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.dto.workout.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.workout.WorkoutTemplateExerciseRequestDTO;
import com.beefit.api.dto.workout.WorkoutTemplateExerciseResponseDTO;
import com.beefit.api.dto.workout.WorkoutTemplateResponseDTO;
import com.beefit.api.exception.exercises.CatalogExerciseNotFound;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.exception.workout.WorkoutTemplateNotFoundException;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.dto.social.SocialEventDTO;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class WorkoutTemplateService {

    private final WorkoutTemplateRepository WTRepository;
    private final UserRepository userRepository;
    private final CatalogExerciseRepository catalogExerciseRepository;
    private final PostRepository postRepository;
    private final WorkoutMuscleGroupMapper workoutMuscleGroupMapper;
    private final SseService sseService;

    @Transactional
    // Lembrar de implementar o id do amigo na parte do social
    public WorkoutTemplateResponseDTO createTemplate(WorkoutTemplateRequestDTO requestDTO, UUID userID /*
                                                                                                        * , UUID
                                                                                                        * friendID
                                                                                                        */) {
        WorkoutTemplate template = new WorkoutTemplate();
        template.setName(requestDTO.name());
        template.setMuscleGroupsList(workoutMuscleGroupMapper.toDomain(requestDTO.muscleGroups()));

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

    @Transactional
    public WorkoutTemplateResponseDTO updateTemplate(UUID templateId, WorkoutTemplateRequestDTO requestDTO, UUID userID) {
        WorkoutTemplate template = WTRepository.findById(templateId)
                .orElseThrow(WorkoutTemplateNotFoundException::new);
        validateTemplateAccess(template, userID);

        template.setName(requestDTO.name());
        template.setMuscleGroupsList(workoutMuscleGroupMapper.toDomain(requestDTO.muscleGroups()));
        if (template.getExerciseList() == null) {
            template.setExerciseList(new ArrayList<>());
        }
        template.getExerciseList().clear();
        template.getExerciseList().addAll(buildTemplateExercises(requestDTO.exercises(), template));

        return toResponse(WTRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(UUID templateId, UUID userID) {
        WorkoutTemplate template = WTRepository.findById(templateId)
                .orElseThrow(WorkoutTemplateNotFoundException::new);
        validateTemplateAccess(template, userID);

        WTRepository.delete(template);
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

    private void validateTemplateAccess(WorkoutTemplate template, UUID userID) {
        if (template.getUser() == null || template.getUser().getId() == null
                || !template.getUser().getId().equals(userID)) {
            throw new WorkoutTemplateNotFoundException();
        }
    }

    public WorkoutTemplateResponseDTO importTemplateFromPost(UUID postId, String name, UUID currentUserId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(PostNotFoundException::new);

        WorkoutSession session = post.getWorkoutSession();

        List<MuscleGroups> importedMuscleGroups = session.getMuscleGroupsList();

        WorkoutTemplate newTemplate = new WorkoutTemplate();
        newTemplate.setName(name != null && !name.isBlank() ? name.trim() : (post.getTitle() != null && !post.getTitle().isBlank() ? post.getTitle().trim() : "Imported from " + post.getUser().getFullName()));

        User user = userRepository.findById(currentUserId).orElseThrow(UserNotFoundException::new);
        newTemplate.setUser(user);
        newTemplate.setMuscleGroupsList(importedMuscleGroups);

        List<TemplateExercise> importedExercises = session.getExercises().stream()
                .map(sessionExercise -> {
                    TemplateExercise templateEx = new TemplateExercise();

                    templateEx.setCatalogExercise(sessionExercise.getExercise());

                    templateEx.setWorkoutTemplate(newTemplate);
                    templateEx.setOrder(sessionExercise.getOrder());

                    return templateEx;
                })
                .collect(Collectors.toList());

        newTemplate.setExerciseList(importedExercises);

        WorkoutTemplate savedTemplate = WTRepository.save(newTemplate);

        WorkoutTemplateResponseDTO responseDTO = new WorkoutTemplateResponseDTO(
                savedTemplate.getId(),
                savedTemplate.getName(),
                savedTemplate.getMuscleGroupsList(),
                savedTemplate.getExerciseList().stream()
                        .map(templateExercise -> new WorkoutTemplateExerciseResponseDTO(
                                templateExercise.getCatalogExercise().getId(),
                                templateExercise.getCatalogExercise().getName(),
                                templateExercise.getOrder()))
                        .toList()
        );

        sseService.broadcastToFriend(new SocialEventDTO<>(SocialEvent.TEMPLATE_IMPORTED, responseDTO), currentUserId);

        return responseDTO;
    }
}
