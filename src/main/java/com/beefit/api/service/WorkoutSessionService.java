package com.beefit.api.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.DataSocialEventDTO;
import com.beefit.api.dto.FinishWorkoutExerciseDTO;
import com.beefit.api.dto.FinishWorkoutSetDTO;
import com.beefit.api.dto.FinishWorkoutSnapshotRequestDTO;
import com.beefit.api.dto.SocialEventDTO;
import com.beefit.api.dto.StartWorkoutRequestDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;
import com.beefit.api.exception.CatalogExerciseNotFound;
import com.beefit.api.exception.InvalidWorkoutSessionStateException;
import com.beefit.api.exception.InvalidWorkoutSnapshotException;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.exception.WorkoutAlreadyInProgressException;
import com.beefit.api.exception.WorkoutSessionNotFoundException;
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
    private final SseService sseService;

    @Transactional
    public WorkoutSessionResponseDTO startWorkout(UUID userId, StartWorkoutRequestDTO request) {
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

        DataSocialEventDTO dataSocialEventDTO = new DataSocialEventDTO(user.getId(), user.getUsername(),
                savedSession.getStartedAt());

        sseService.broadcast(new SocialEventDTO(SocialEvent.WORKOUT_STARTED, dataSocialEventDTO));

        return workoutSessionMapper.toDto(savedSession);
    }

    @Transactional
    public WorkoutSessionResponseDTO finishWorkout(UUID userId, UUID workoutId,
            FinishWorkoutSnapshotRequestDTO request) {
        WorkoutSession session = workoutSessionRepository.findById(workoutId)
                .orElseThrow(WorkoutSessionNotFoundException::new);
        initializeWorkoutGraph(session);

        validateWorkoutOwnership(userId, session);
        validateWorkoutInProgress(session);
        validateFinishedAt(session, request.finishedAt());

        session.setMuscleGroupsList(request.muscleGroups());
        reconcileExercises(session, request.exercises());
        session.setFinishedAt(request.finishedAt());
        session.setDurationSeconds(calculateDurationSeconds(session.getStartedAt(), request.finishedAt()));
        session.setTotalVolume(calculateTotalVolume(session));
        session.setStatus(WorkoutStatus.FINISHED);

        WorkoutSession savedSession = workoutSessionRepository.save(session);
        return workoutSessionMapper.toDto(savedSession);
    }

    private List<SessionExercise> resolveInitialExercises(UUID userId, StartWorkoutRequestDTO request) {
        if (request.templateId() == null) {
            return List.of();
        }
        return buildExercisesFromTemplate(userId, request.templateId());
    }

    // Revisar método. Eu (Ryan) troquei de TemplateExercise para CatalogExercise
    // pois estava dando
    // problema. Confere a estrutura do WorkoutTemplate lá também
    private List<SessionExercise> buildExercisesFromTemplate(UUID userId, UUID templateId) {
        WorkoutTemplate template = workoutTemplateRepository.findById(templateId)
                .orElseThrow(WorkoutTemplateNotFoundException::new);
        validateTemplateAccess(userId, template);

        List<SessionExercise> exercises = new ArrayList<>();

        for (CatalogExercise catalogExercise : template.getExerciseList()) {
            SessionExercise sessionExercise = new SessionExercise();
            sessionExercise.setExercise(catalogExercise);
            sessionExercise.setOrder(catalogExercise.getOrder());
            exercises.add(sessionExercise);
        }

        return exercises;
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

    private void validateWorkoutOwnership(UUID userId, WorkoutSession session) {
        if (session.getUser() == null || session.getUser().getId() == null
                || !session.getUser().getId().equals(userId)) {
            throw new WorkoutSessionNotFoundException();
        }
    }

    private void validateWorkoutInProgress(WorkoutSession session) {
        if (session.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new InvalidWorkoutSessionStateException("A sessão de treino não está em andamento.");
        }
    }

    private void validateFinishedAt(WorkoutSession session, LocalDateTime finishedAt) {
        if (finishedAt.isBefore(session.getStartedAt())) {
            throw new InvalidWorkoutSnapshotException(
                    "A data de finalização não pode ser anterior ao início do treino.");
        }
    }

    private void initializeWorkoutGraph(WorkoutSession session) {
        Hibernate.initialize(session.getExercises());
        for (SessionExercise exercise : session.getExercises()) {
            Hibernate.initialize(exercise.getExercise());
            Hibernate.initialize(exercise.getSets());
        }
    }

    private void reconcileExercises(WorkoutSession session, List<FinishWorkoutExerciseDTO> requestedExercises) {
        validateUniqueExerciseOrders(requestedExercises);
        validateUniqueExerciseIds(requestedExercises);

        Map<UUID, SessionExercise> existingExercisesById = new HashMap<>();
        for (SessionExercise exercise : session.getExercises()) {
            existingExercisesById.put(exercise.getId(), exercise);
        }

        for (FinishWorkoutExerciseDTO requestedExercise : requestedExercises) {
            SessionExercise sessionExercise = resolveSessionExercise(session, requestedExercise, existingExercisesById);
            sessionExercise.setOrder(requestedExercise.order());
            reconcileSets(sessionExercise, requestedExercise.sets());
        }

        session.getExercises().sort(Comparator.comparing(SessionExercise::getOrder));
    }

    private SessionExercise resolveSessionExercise(WorkoutSession session,
            FinishWorkoutExerciseDTO requestedExercise,
            Map<UUID, SessionExercise> existingExercisesById) {
        if (requestedExercise.sessionExerciseId() == null) {
            CatalogExercise catalogExercise = catalogExerciseRepository.findById(requestedExercise.catalogExerciseId())
                    .orElseThrow(CatalogExerciseNotFound::new);
            SessionExercise sessionExercise = new SessionExercise();
            sessionExercise.setExercise(catalogExercise);
            sessionExercise.setOrder(requestedExercise.order());
            session.addExercise(sessionExercise);
            return sessionExercise;
        }

        SessionExercise sessionExercise = existingExercisesById.get(requestedExercise.sessionExerciseId());
        if (sessionExercise == null) {
            throw new InvalidWorkoutSnapshotException("O exercício informado não pertence à sessão de treino.");
        }

        if (!Objects.equals(sessionExercise.getExercise().getId(), requestedExercise.catalogExerciseId())) {
            throw new InvalidWorkoutSnapshotException("Não é permitido trocar o exercício de um item já existente.");
        }

        return sessionExercise;
    }

    private void reconcileSets(SessionExercise sessionExercise, List<FinishWorkoutSetDTO> requestedSets) {
        validateUniqueSetOrders(requestedSets);
        validateUniqueSetIds(requestedSets);

        Map<UUID, com.beefit.api.domain.Set> existingSetsById = new HashMap<>();
        for (com.beefit.api.domain.Set set : sessionExercise.getSets()) {
            existingSetsById.put(set.getId(), set);
        }

        Set<UUID> retainedSetIds = new HashSet<>();

        for (FinishWorkoutSetDTO requestedSet : requestedSets) {
            com.beefit.api.domain.Set set = resolveSet(sessionExercise, requestedSet, existingSetsById);
            set.setOrder(requestedSet.order());
            set.setWeightKg(requestedSet.weightKg());
            set.setReps(requestedSet.reps());

            if (set.getId() != null) {
                retainedSetIds.add(set.getId());
            }
        }

        sessionExercise.getSets().removeIf(set -> set.getId() != null && !retainedSetIds.contains(set.getId()));
        sessionExercise.getSets().sort(Comparator.comparing(com.beefit.api.domain.Set::getOrder));
    }

    private com.beefit.api.domain.Set resolveSet(SessionExercise sessionExercise,
            FinishWorkoutSetDTO requestedSet,
            Map<UUID, com.beefit.api.domain.Set> existingSetsById) {
        if (requestedSet.id() == null) {
            com.beefit.api.domain.Set set = new com.beefit.api.domain.Set();
            set.setIsPersonalRecord(Boolean.FALSE);
            sessionExercise.addSet(set);
            return set;
        }

        com.beefit.api.domain.Set set = existingSetsById.get(requestedSet.id());
        if (set == null) {
            throw new InvalidWorkoutSnapshotException("A série informada não pertence ao exercício selecionado.");
        }

        return set;
    }

    private Integer calculateDurationSeconds(LocalDateTime startedAt, LocalDateTime finishedAt) {
        return Math.toIntExact(java.time.Duration.between(startedAt, finishedAt).getSeconds());
    }

    private Double calculateTotalVolume(WorkoutSession session) {
        return session.getExercises().stream()
                .flatMap(exercise -> exercise.getSets().stream())
                .mapToDouble(set -> set.getWeightKg() * set.getReps())
                .sum();
    }

    private void validateUniqueExerciseOrders(List<FinishWorkoutExerciseDTO> requestedExercises) {
        Set<Integer> seenOrders = new HashSet<>();
        for (FinishWorkoutExerciseDTO exercise : requestedExercises) {
            if (!seenOrders.add(exercise.order())) {
                throw new InvalidWorkoutSnapshotException("Existem exercícios com a mesma ordem no snapshot.");
            }
        }
    }

    private void validateUniqueExerciseIds(List<FinishWorkoutExerciseDTO> requestedExercises) {
        Set<UUID> seenIds = new HashSet<>();
        for (FinishWorkoutExerciseDTO exercise : requestedExercises) {
            if (exercise.sessionExerciseId() != null && !seenIds.add(exercise.sessionExerciseId())) {
                throw new InvalidWorkoutSnapshotException("O snapshot contém exercícios duplicados.");
            }
        }
    }

    private void validateUniqueSetOrders(List<FinishWorkoutSetDTO> requestedSets) {
        Set<Integer> seenOrders = new HashSet<>();
        for (FinishWorkoutSetDTO set : requestedSets) {
            if (!seenOrders.add(set.order())) {
                throw new InvalidWorkoutSnapshotException("Existem séries com a mesma ordem no exercício.");
            }
        }
    }

    private void validateUniqueSetIds(List<FinishWorkoutSetDTO> requestedSets) {
        Set<UUID> seenIds = new HashSet<>();
        for (FinishWorkoutSetDTO set : requestedSets) {
            if (set.id() != null && !seenIds.add(set.id())) {
                throw new InvalidWorkoutSnapshotException("O snapshot contém séries duplicadas.");
            }
        }
    }
}
