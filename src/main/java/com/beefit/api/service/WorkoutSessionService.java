package com.beefit.api.service;

import com.beefit.api.domain.*;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.dto.workout.*;
import com.beefit.api.exception.exercises.CatalogExerciseNotFound;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.exception.workout.*;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.mapper.WorkoutSessionMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkoutSessionService {
    private static final int MIN_HONEY_WORKOUT_DURATION_SECONDS = 1800;

    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final CatalogExerciseRepository catalogExerciseRepository;
    private final UserRepository userRepository;
    private final WorkoutSessionMapper workoutSessionMapper;
    private final WorkoutMuscleGroupMapper workoutMuscleGroupMapper;
    private final CardService cardService;
    private final SseService sseService;
    private final com.beefit.api.repository.PostRepository postRepository;
    private final FriendshipService friendshipService;

    @Transactional(readOnly = true)
    public Optional<WorkoutSessionResponseDTO> getActiveWorkout(UUID userId) {
        return workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)
                .stream()
                .findFirst()
                .map(session -> {
                    initializeWorkoutGraph(session);
                    return workoutSessionMapper.toDto(session);
                });
    }

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
        session.setTitle(null);
        session.setShouldPostWorkout(false);
        session.setIsPrivate(request.isPrivate());
        session.setStatus(WorkoutStatus.IN_PROGRESS);

        List<SessionExercise> sessionExercises = resolveInitialExercises(userId, request);

        for (SessionExercise sessionExercise : sessionExercises) {
            session.addExercise(sessionExercise);
        }

        WorkoutSession savedSession = workoutSessionRepository.save(session);

        broadcastIfPublic(savedSession, SocialEvent.WORKOUT_STARTED, savedSession.getStartedAt());

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
        int durationSeconds = calculateDurationSeconds(session.getStartedAt(), request.finishedAt());
        session.setDurationSeconds(durationSeconds);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        double durationMinutes = durationSeconds / 60.0;

        double MET = 6.0;
        double caloriesBurned = durationMinutes * (MET * user.getBodyWeight() / 60.0);
        session.setEstimatedCalories(caloriesBurned);

        session.setTotalVolume(calculateTotalVolume(session));

        Card card = cardService.generateCard(user, session);

        session.setStatus(WorkoutStatus.FINISHED);
        session.setCard(card);

        user.setWorkoutsCount(user.getWorkoutsCount() + 1);
        if (durationSeconds >= MIN_HONEY_WORKOUT_DURATION_SECONDS) {
            user.setOffensiveHoneys(user.getOffensiveHoneys() + 1);
            user.setWeeklyWorkoutsCount(user.getWeeklyWorkoutsCount() + 1);
        }
        userRepository.save(user);
        // Geracao de posts
        // Só publica se o treino NÃO for privado E se o usuário NÃO estiver em Modo Fantasma
        if (!session.getIsPrivate() && !user.getGhostMode()) {
            com.beefit.api.domain.Post autoPost = new com.beefit.api.domain.Post();
            autoPost.setUser(user);
            autoPost.setWorkoutSession(session);
            autoPost.setTotalLikes(0);
            autoPost.setIsRemoved(false);

            postRepository.save(autoPost);
        }
        WorkoutSession savedSession = workoutSessionRepository.save(session);

        broadcastIfPublic(savedSession, SocialEvent.WORKOUT_FINISHED, request.finishedAt());
        return workoutSessionMapper.toDto(savedSession);
    }

    @Transactional
    public WorkoutSessionResponseDTO cancelWorkout(UUID userId, UUID workoutId) {
        WorkoutSession session = workoutSessionRepository.findById(workoutId)
                .orElseThrow(WorkoutSessionNotFoundException::new);
        initializeWorkoutGraph(session);

        validateWorkoutOwnership(userId, session);
        validateWorkoutInProgress(session);

        LocalDateTime canceledAt = LocalDateTime.now();
        session.setFinishedAt(canceledAt);
        session.setDurationSeconds(null);
        session.setEstimatedCalories(null);
        session.setTotalVolume(null);
        session.setStatus(WorkoutStatus.CANCELED);

        WorkoutSession savedSession = workoutSessionRepository.save(session);

        broadcastIfPublic(savedSession, SocialEvent.WORKOUT_CANCELED, canceledAt);
        return workoutSessionMapper.toDto(savedSession);
    }

    @Transactional
    public WorkoutSessionResponseDTO addMuscleGroup(UUID userId, UUID workoutId, String muscleGroupValue) {
        WorkoutSession session = workoutSessionRepository.findById(workoutId)
                .orElseThrow(WorkoutSessionNotFoundException::new);

        validateWorkoutOwnership(userId, session);
        validateWorkoutInProgress(session);

        MuscleGroups muscleGroup = parseMuscleGroup(muscleGroupValue);
        if (session.getMuscleGroupsList().contains(muscleGroup)) {
            return workoutSessionMapper.toDto(session);
        }

        session.getMuscleGroupsList().add(muscleGroup);
        WorkoutSession savedSession = workoutSessionRepository.save(session);

        broadcastIfPublic(savedSession, SocialEvent.WORKOUT_MUSCLE_GROUP_ADDED, LocalDateTime.now());
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

        for (TemplateExercise templateExercise : template.getExerciseList()) {
            SessionExercise sessionExercise = new SessionExercise();
            sessionExercise.setExercise(templateExercise.getCatalogExercise());
            sessionExercise.setOrder(templateExercise.getOrder());
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

        Set<UUID> retainedExerciseIds = new HashSet<>();
        for (FinishWorkoutExerciseDTO requestedExercise : requestedExercises) {
            SessionExercise sessionExercise = resolveSessionExercise(session, requestedExercise, existingExercisesById);
            sessionExercise.setOrder(requestedExercise.order());
            reconcileSets(sessionExercise, requestedExercise.sets());

            if (sessionExercise.getId() != null) {
                retainedExerciseIds.add(sessionExercise.getId());
            }
        }

        List<SessionExercise> omittedExercises = session.getExercises().stream()
                .filter(exercise -> exercise.getId() != null && !retainedExerciseIds.contains(exercise.getId()))
                .toList();
        omittedExercises.forEach(session::removeExercise);
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

    private MuscleGroups parseMuscleGroup(String muscleGroupValue) {
        try {
            return workoutMuscleGroupMapper.toDomain(muscleGroupValue);
        } catch (IllegalArgumentException exception) {
            throw new InvalidMuscleGroupException(muscleGroupValue);
        }
    }

    private void broadcastIfPublic(WorkoutSession session, SocialEvent eventType, LocalDateTime occurredAt) {
        if (Boolean.TRUE.equals(session.getIsPrivate())) {
            return;
        }

        User user = session.getUser();
        WorkoutPresenceEventDTO presenceEvent = new WorkoutPresenceEventDTO(
                eventType.name(),
                session.getId(),
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                session.getStatus().getStatus(),
                occurredAt,
                workoutMuscleGroupMapper.toApi(session.getMuscleGroupsList()));

        List<User> friends = friendshipService.getFriends(user.getId()).orElseThrow();

        sseService.broadcastToFriends(new SocialEventDTO<>(eventType, presenceEvent), friends);
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
