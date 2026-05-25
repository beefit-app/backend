package com.beefit.api.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.StartWorkoutExerciseItemDTO;
import com.beefit.api.dto.StartWorkoutRequestDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;
import com.beefit.api.exception.CatalogExerciseNotFound;
import com.beefit.api.exception.InvalidWorkoutSessionStateException;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.exception.WorkoutAlreadyInProgressException;
import com.beefit.api.exception.WorkoutTemplateNotFoundException;
import com.beefit.api.mapper.WorkoutSessionMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkoutSessionService {
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final CatalogExerciseRepository catalogExerciseRepository;
    private final UserRepository userRepository;
    private final WorkoutSessionMapper workoutSessionMapper;

    @Transactional
    public WorkoutSessionResponseDTO startWorkout(UUID userId, StartWorkoutRequestDTO request) {
        validateExerciseSource(request);
        validateNoWorkoutInProgress(userId);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());
        session.setFinishedAt(null);
        session.setDurationSeconds(null);
        session.setEstimatedCalories(null);
        session.setTotalVolume(null);
        session.setMuscleGroupsList(request.muscleGroups());
        session.setIsPrivate(request.isPrivate());
        session.setStatus(WorkoutStatus.IN_PROGRESS);

        List<SessionExercise> sessionExercises = resolveInitialExercises(userId, request);

        for (SessionExercise sessionExercise : sessionExercises) {
            session.addExercise(sessionExercise);
        }

        WorkoutSession savedSession = workoutSessionRepository.save(session);

        return workoutSessionMapper.toDto(savedSession);
    }

    // Esse método é o responsável por definir como o treino ativo trata os
    // exercícios,
    // ou seja, caso haja um template pré-definido, o treino já mostra os exercícios
    // corretamente
    private List<SessionExercise> resolveInitialExercises(UUID userId, StartWorkoutRequestDTO request) {
        if (request.templateId() == null) {
            return buildExercisesFromRequest(request.exercises());
        }
        return buildExercisesFromTemplate(userId, request.templateId());
    }

    private List<SessionExercise> buildExercisesFromTemplate(UUID userId, UUID templateId) {
        WorkoutTemplate template = workoutTemplateRepository.findById(templateId)
                .orElseThrow(WorkoutTemplateNotFoundException::new);
        validateTemplateAccess(userId, template);

        List<SessionExercise> exercises = new ArrayList<>();

        for (TemplateExercise templateExercise : template.getExercises()) {
            SessionExercise sessionExercise = new SessionExercise();
            sessionExercise.setExercise(templateExercise.getCatalogExercise());
            sessionExercise.setOrder(templateExercise.getOrder());
            exercises.add(sessionExercise);
        }

        return exercises;
    }

    private List<SessionExercise> buildExercisesFromRequest(List<StartWorkoutExerciseItemDTO> items) {
        validateExerciseOrders(items);
        List<SessionExercise> exercises = new ArrayList<>();

        for (StartWorkoutExerciseItemDTO item : items) {
            CatalogExercise catalogExercise = catalogExerciseRepository.findById(item.catalogExerciseId())
                    .orElseThrow(CatalogExerciseNotFound::new);
            SessionExercise sessionExercise = new SessionExercise();
            sessionExercise.setExercise(catalogExercise);
            sessionExercise.setOrder(item.order());
            exercises.add(sessionExercise);
        }

        return exercises;
    }

    private void validateExerciseSource(StartWorkoutRequestDTO request) {
        boolean hasTemplate = request.templateId() != null;
        boolean hasExercises = request.exercises() != null && !request.exercises().isEmpty();

        if (hasTemplate == hasExercises) {
            throw new InvalidWorkoutSessionStateException(
                    "Informe exatamente uma origem de exercícios: templateId ou exercises.");
        }
    }

    private void validateExerciseOrders(List<StartWorkoutExerciseItemDTO> items) {
        Set<Integer> usedOrders = new HashSet<>();

        for (StartWorkoutExerciseItemDTO item : items) {
            if (!usedOrders.add(item.order())) {
                throw new InvalidWorkoutSessionStateException("A ordem dos exercícios não pode ser duplicada.");
            }
        }
    }

    private void validateTemplateAccess(UUID userId, WorkoutTemplate template) {
        if (template.getUser() == null || template.getUser().getId() == null
                || !template.getUser().getId().equals(userId)) {
            throw new WorkoutTemplateNotFoundException();
        }
    }

    private void validateNoWorkoutInProgress(UUID userId) {
        boolean workoutInProgress = workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)
                .stream().findAny().isPresent();

        if (workoutInProgress) {
            throw new WorkoutAlreadyInProgressException();
        }
    }
}
