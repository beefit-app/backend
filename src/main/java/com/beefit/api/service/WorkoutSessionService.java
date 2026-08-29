package com.beefit.api.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.Card;
import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.dto.workout.CardGeneratedEventDTO;
import com.beefit.api.dto.workout.FinishWorkoutExerciseDTO;
import com.beefit.api.dto.workout.FinishWorkoutSetDTO;
import com.beefit.api.dto.workout.FinishWorkoutSnapshotRequestDTO;
import com.beefit.api.dto.workout.StartWorkoutExerciseItemDTO;
import com.beefit.api.dto.workout.StartWorkoutRequestDTO;
import com.beefit.api.dto.workout.WorkoutPresenceEventDTO;
import com.beefit.api.dto.workout.WorkoutSessionResponseDTO;
import com.beefit.api.exception.exercises.CatalogExerciseNotFound;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.exception.workout.InvalidMuscleGroupException;
import com.beefit.api.exception.workout.IdempotencyConflictException;
import com.beefit.api.exception.workout.InvalidWorkoutSessionStateException;
import com.beefit.api.exception.workout.InvalidWorkoutSnapshotException;
import com.beefit.api.exception.workout.WorkoutAlreadyInProgressException;
import com.beefit.api.exception.workout.WorkoutSessionNotFoundException;
import com.beefit.api.exception.workout.WorkoutTemplateNotFoundException;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.mapper.WorkoutSessionMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;
import com.beefit.api.repository.PostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkoutSessionService {
    private static final int MIN_HONEY_WORKOUT_DURATION_SECONDS = 1800;
    private static final Duration MAX_FUTURE_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration MAX_WORKOUT_DURATION = Duration.ofHours(24);

    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final CatalogExerciseRepository catalogExerciseRepository;
    private final UserRepository userRepository;
    private final WorkoutSessionMapper workoutSessionMapper;
    private final WorkoutMuscleGroupMapper workoutMuscleGroupMapper;
    private final CardService cardService;
    private final SseService sseService;
    private final PostRepository postRepository;
    private final FriendshipService friendshipService;
    private final WorkoutMediaService workoutMediaService;
    private final Clock businessClock;

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

    @Transactional(readOnly = true)
    public WorkoutSessionResponseDTO getWorkout(UUID userId, UUID workoutId) {
        WorkoutSession session = workoutSessionRepository.findById(workoutId)
                .orElseThrow(WorkoutSessionNotFoundException::new);
        validateWorkoutOwnership(userId, session);
        initializeWorkoutGraph(session);
        return workoutSessionMapper.toDto(session);
    }

    @Transactional(readOnly = true)
    public List<WorkoutPresenceEventDTO> getPublicActiveWorkouts(UUID currentUserId) {
        return workoutSessionRepository.findAllVisibleToFriendByStatusExcludingUser(WorkoutStatus.IN_PROGRESS, currentUserId)
                .stream()
                .map(session -> new WorkoutPresenceEventDTO(
                        SocialEvent.WORKOUT_STARTED.name(),
                        session.getId(),
                        session.getUser().getId(),
                        session.getUser().getUsername(),
                        session.getUser().getFullName(),
                        session.getUser().getProfilePicture(),
                        session.getStatus().getStatus(),
                        session.getStartedAt(),
                        workoutMuscleGroupMapper.toApi(session.getMuscleGroupsList())))
                .toList();
    }

    @Transactional
    public WorkoutSessionResponseDTO startWorkout(UUID userId, StartWorkoutRequestDTO request) {
        validateNoWorkoutInProgress(userId);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now(businessClock));
        session.setFinishedAt(null);
        session.setDurationSeconds(null);
        session.setEstimatedCalories(null);
        session.setTotalVolume(null);
        session.setMuscleGroupsList(workoutMuscleGroupMapper.toDomain(request.muscleGroups()));
        session.setTitle(null);
        session.setShouldPostWorkout(false);
        session.setIsPrivate(request.isPrivate());
        session.setStatus(WorkoutStatus.IN_PROGRESS);

        List<SessionExercise> sessionExercises = resolveInitialExercises(userId, request);

        for (SessionExercise sessionExercise : sessionExercises) {
            session.addExercise(sessionExercise);
        }

        WorkoutSession savedSession;
        try {
            savedSession = workoutSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            throw new WorkoutAlreadyInProgressException();
        }

        broadcastIfPublic(savedSession, SocialEvent.WORKOUT_STARTED, savedSession.getStartedAt());

        return workoutSessionMapper.toDto(savedSession);
    }

    @Transactional
    public WorkoutSessionResponseDTO finishWorkout(UUID userId, UUID workoutId, String idempotencyKey,
            FinishWorkoutSnapshotRequestDTO request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new InvalidWorkoutSnapshotException("Idempotency-Key inválida.");
        }

        workoutSessionRepository.findByUserIdAndFinishIdempotencyKey(userId, idempotencyKey)
                .filter(existing -> !existing.getId().equals(workoutId))
                .ifPresent(existing -> {
                    throw new IdempotencyConflictException(
                            "A Idempotency-Key já foi usada para finalizar outro treino.");
                });

        WorkoutSession session = workoutSessionRepository.findByIdForFinish(workoutId)
                .orElseThrow(WorkoutSessionNotFoundException::new);
        initializeWorkoutGraph(session);
        String requestHash = hashFinishRequest(request);

        validateWorkoutOwnership(userId, session);
        if (session.getStatus() == WorkoutStatus.FINISHED
                && idempotencyKey.equals(session.getFinishIdempotencyKey())) {
            if (!requestHash.equals(session.getFinishRequestHash())) {
                throw new IdempotencyConflictException("A Idempotency-Key já foi usada com outro conteúdo.");
            }
            return workoutSessionMapper.toDto(session);
        }

        LocalDateTime finishedAt = parseFinishedAt(request.finishedAt());

        validateWorkoutInProgress(session);
        finishedAt = validateAndNormalizeFinishedAt(session, finishedAt, LocalDateTime.now(businessClock));

        session.setFinishIdempotencyKey(idempotencyKey);
        session.setFinishRequestHash(requestHash);

        session.setTitle(request.title());
        session.setShouldPostWorkout(Boolean.TRUE.equals(request.shouldPostWorkout()));
        reconcileExercises(session, request.exercises());
        session.setMuscleGroupsList(filterMuscleGroupsWithoutExercises(
                workoutMuscleGroupMapper.toDomain(request.muscleGroups()), session));

        session.setFinishedAt(finishedAt);
        int durationSeconds = calculateDurationSeconds(session.getStartedAt(), finishedAt);
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

        LocalDateTime honeyDayStart = finishedAt.toLocalDate().atStartOfDay();
        boolean eligibleForHoney = isEligibleForHoney(session, durationSeconds);
        if (eligibleForHoney) {
            user.setWorkoutsCount(user.getWorkoutsCount() + 1);
        }
        boolean alreadyEarnedHoneyToday = eligibleForHoney
                && workoutSessionRepository.existsEligibleFinishedWorkoutOnDay(
                        userId,
                        session.getId(),
                        WorkoutStatus.FINISHED,
                        MIN_HONEY_WORKOUT_DURATION_SECONDS,
                        honeyDayStart,
                        honeyDayStart.plusDays(1));
        if (eligibleForHoney && !alreadyEarnedHoneyToday) {
            user.setOffensiveHoneys(user.getOffensiveHoneys() + 1);
            user.setWeeklyWorkoutsCount(user.getWeeklyWorkoutsCount() + 1);
        }
        userRepository.save(user);
        // Geracao de posts
        // Só publica se o treino NÃO for privado E se o usuário NÃO estiver em Modo
        // Fantasma
        if (Boolean.TRUE.equals(session.getShouldPostWorkout()) && !session.getIsPrivate() && !user.getGhostMode()) {
            com.beefit.api.domain.Post autoPost = new com.beefit.api.domain.Post();
            autoPost.setUser(user);
            autoPost.setWorkoutSession(session);
            autoPost.setTotalLikes(0);
            autoPost.setIsRemoved(false);
            autoPost.setTitle(request.title());
            if (request.photoMediaId() != null) {
                workoutMediaService.consume(request.photoMediaId(), userId);
                autoPost.setPhotoMediaId(request.photoMediaId());
            }

            postRepository.save(autoPost);
        }
        WorkoutSession savedSession;
        try {
            savedSession = workoutSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            throw new IdempotencyConflictException("A Idempotency-Key já foi usada em outra finalização.");
        }

        broadcastCardGenerated(userId, savedSession, card);
        broadcastIfPublic(savedSession, SocialEvent.WORKOUT_FINISHED, finishedAt);
        return workoutSessionMapper.toDto(savedSession);
    }

    private String hashFinishRequest(FinishWorkoutSnapshotRequestDTO request) {
        try {
            String canonicalPayload = request.finishedAt() + "|" + request.muscleGroups() + "|" + request.exercises()
                    + "|" + request.title() + "|" + request.shouldPostWorkout() + "|" + request.photoMediaId();
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não disponível.", exception);
        }
    }

    private void broadcastCardGenerated(UUID userId, WorkoutSession session, Card card) {
        if (card == null) return;

        sseService.broadcastToFriend(
                new SocialEventDTO<>(
                        SocialEvent.CARD_GENERATED,
                        new CardGeneratedEventDTO(
                                session.getId(),
                                card.getId(),
                                card.getExercise().getId(),
                                card.getExercise().getName(),
                                card.getBestSetWeightKg(),
                                card.getBestSetReps(),
                                card.getEstimatedOneRepMax(),
                                card.getImprovementPercentage(),
                                card.getCardRarity().name(),
                                workoutMuscleGroupMapper.toApi(card.getMuscleGroup()),
                                card.getMuscleIntensities()
                        )
                ),
                userId
        );
    }

    private LocalDateTime parseFinishedAt(String finishedAtValue) {
        if (finishedAtValue == null || finishedAtValue.isBlank()) {
            throw new InvalidWorkoutSnapshotException("A data de finalização é obrigatória.");
        }

        try {
            return OffsetDateTime.parse(finishedAtValue)
                    .atZoneSameInstant(businessClock.getZone())
                    .toLocalDateTime();
        } catch (RuntimeException ignored) {
            // Tenta formatos sem offset para compatibilidade transitória.
        }

        try {
            return Instant.parse(finishedAtValue)
                    .atZone(businessClock.getZone())
                    .toLocalDateTime();
        } catch (RuntimeException ignored) {
            // Tenta formato local como último fallback.
        }

        try {
            return LocalDateTime.parse(finishedAtValue);
        } catch (RuntimeException ignored) {
            throw new InvalidWorkoutSnapshotException("Formato inválido para a data de finalização do treino.");
        }
    }

    @Transactional
    public WorkoutSessionResponseDTO cancelWorkout(UUID userId, UUID workoutId) {
        WorkoutSession session = workoutSessionRepository.findById(workoutId)
                .orElseThrow(WorkoutSessionNotFoundException::new);
        initializeWorkoutGraph(session);

        validateWorkoutOwnership(userId, session);
        validateWorkoutInProgress(session);

        LocalDateTime canceledAt = LocalDateTime.now(businessClock);
        session.setFinishedAt(canceledAt);
        session.setStatus(WorkoutStatus.CANCELED);

        broadcastIfPublic(session, SocialEvent.WORKOUT_CANCELED, canceledAt);

        workoutSessionRepository.delete(session);

        return workoutSessionMapper.toDto(session);
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

        broadcastIfPublic(savedSession, SocialEvent.WORKOUT_MUSCLE_GROUP_ADDED, LocalDateTime.now(businessClock));
        return workoutSessionMapper.toDto(savedSession);
    }

    private List<SessionExercise> resolveInitialExercises(UUID userId, StartWorkoutRequestDTO request) {
        if (request.templateId() != null) {
            return buildExercisesFromTemplate(userId, request.templateId());
        }

        return buildExercisesFromRequest(request.exercises());
    }

    private List<SessionExercise> buildExercisesFromRequest(List<StartWorkoutExerciseItemDTO> requestedExercises) {
        if (requestedExercises == null || requestedExercises.isEmpty()) {
            return List.of();
        }

        validateUniqueInitialExerciseOrders(requestedExercises);

        List<SessionExercise> exercises = new ArrayList<>();
        for (StartWorkoutExerciseItemDTO requestedExercise : requestedExercises) {
            CatalogExercise catalogExercise = catalogExerciseRepository
                    .findById(requestedExercise.catalogExerciseId())
                    .orElseThrow(CatalogExerciseNotFound::new);

            SessionExercise sessionExercise = new SessionExercise();
            sessionExercise.setExercise(catalogExercise);
            sessionExercise.setOrder(requestedExercise.order());
            exercises.add(sessionExercise);
        }

        exercises.sort(Comparator.comparing(SessionExercise::getOrder));
        return exercises;
    }

    private void validateUniqueInitialExerciseOrders(List<StartWorkoutExerciseItemDTO> requestedExercises) {
        Set<Integer> seenOrders = new HashSet<>();
        for (StartWorkoutExerciseItemDTO exercise : requestedExercises) {
            if (!seenOrders.add(exercise.order())) {
                throw new InvalidWorkoutSnapshotException("Existem exercícios com a mesma ordem no início do treino.");
            }
        }
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

    private LocalDateTime validateAndNormalizeFinishedAt(
            WorkoutSession session, LocalDateTime finishedAt, LocalDateTime receivedAt) {
        if (finishedAt.isBefore(session.getStartedAt())) {
            throw new InvalidWorkoutSnapshotException(
                    "A data de finalização não pode ser anterior ao início do treino.");
        }

        if (finishedAt.isAfter(receivedAt.plus(MAX_FUTURE_CLOCK_SKEW))) {
            throw new InvalidWorkoutSnapshotException(
                    "A data de finalização não pode estar no futuro.");
        }

        LocalDateTime effectiveFinishedAt = finishedAt.isAfter(receivedAt) ? receivedAt : finishedAt;
        Duration duration = Duration.between(session.getStartedAt(), effectiveFinishedAt);
        if (duration.compareTo(MAX_WORKOUT_DURATION) > 0) {
            throw new InvalidWorkoutSnapshotException(
                    "A duração do treino não pode ultrapassar 24 horas.");
        }

        return effectiveFinishedAt;
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
            CatalogExercise catalogExercise = resolveCatalogExercise(requestedExercise);
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

        UUID requestedCatalogExerciseId = tryParseUuid(requestedExercise.catalogExerciseId());
        if (requestedCatalogExerciseId != null
                && !Objects.equals(sessionExercise.getExercise().getId(), requestedCatalogExerciseId)) {
            throw new InvalidWorkoutSnapshotException("Não é permitido trocar o exercício de um item já existente.");
        }

        return sessionExercise;
    }

    private CatalogExercise resolveCatalogExercise(FinishWorkoutExerciseDTO requestedExercise) {
        UUID catalogExerciseId = tryParseUuid(requestedExercise.catalogExerciseId());
        if (catalogExerciseId != null) {
            return catalogExerciseRepository.findById(catalogExerciseId)
                    .orElseThrow(CatalogExerciseNotFound::new);
        }

        if (requestedExercise.catalogExerciseName() != null && !requestedExercise.catalogExerciseName().isBlank()) {
            return catalogExerciseRepository.findFirstByNameIgnoreCase(requestedExercise.catalogExerciseName().trim())
                    .orElseThrow(() -> new InvalidWorkoutSnapshotException(
                            "Não foi possível identificar o exercício informado para finalizar o treino."));
        }

        throw new InvalidWorkoutSnapshotException(
                "O identificador do exercício é inválido e não foi possível resolver o exercício pelo nome.");
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
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
        return Math.toIntExact(Duration.between(startedAt, finishedAt).getSeconds());
    }

    private boolean isEligibleForHoney(WorkoutSession session, int durationSeconds) {
        if (durationSeconds < MIN_HONEY_WORKOUT_DURATION_SECONDS) {
            return false;
        }

        return session.getExercises().stream()
                .flatMap(exercise -> exercise.getSets().stream())
                .anyMatch(set -> set.getReps() != null && set.getReps() > 0);
    }

    private List<MuscleGroups> filterMuscleGroupsWithoutExercises(
            List<MuscleGroups> selectedGroups, WorkoutSession session) {
        Set<MuscleGroups> exercisedGroups = session.getExercises().stream()
                .flatMap(exercise -> java.util.stream.Stream.concat(
                        Optional.ofNullable(exercise.getExercise().getPrimaryMuscleGroupsList()).orElseGet(List::of).stream(),
                        Optional.ofNullable(exercise.getExercise().getSecondaryMuscleGroupsList()).orElseGet(List::of).stream()))
                .collect(java.util.stream.Collectors.toSet());
        // Catálogos legados podem não ter grupos primários/secundários. Nesse
        // caso não há evidência para excluir um grupo escolhido pelo usuário.
        if (exercisedGroups.isEmpty()) {
            return selectedGroups;
        }
        return selectedGroups.stream().filter(exercisedGroups::contains).toList();
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
                user.getProfilePicture(),
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
