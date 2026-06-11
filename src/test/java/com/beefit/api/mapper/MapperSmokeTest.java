package com.beefit.api.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.SessionExerciseResponseDTO;
import com.beefit.api.dto.SetResponseDTO;
import com.beefit.api.dto.UserResponseDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;

class MapperSmokeTest {

    @Test
    void userResponseMapperMapsUserToDto() {
        UserResponseMapper mapper = Mappers.getMapper(UserResponseMapper.class);
        User user = new User();
        UUID id = UUID.randomUUID();
        user.setId(id);
        user.setFullName("Test User");
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setBodyWeight(80.0f);
        user.setWeeklyGoal(4);

        UserResponseDTO dto = mapper.toDto(user);

        assertEquals(id, dto.id());
        assertEquals("Test User", dto.fullName());
        assertEquals("testuser", dto.username());
        assertEquals("test@example.com", dto.email());
        assertEquals(80.0f, dto.bodyWeight());
        assertEquals(4, dto.weeklyGoal());
    }

    @Test
    void setMapperMapsSetToDto() {
        SetMapper mapper = Mappers.getMapper(SetMapper.class);
        com.beefit.api.domain.Set set = new com.beefit.api.domain.Set();
        UUID id = UUID.randomUUID();
        set.setId(id);
        set.setOrder(1);
        set.setWeightKg(42.5);
        set.setReps(10);
        set.setIsPersonalRecord(Boolean.TRUE);

        SetResponseDTO dto = mapper.toDto(set);

        assertEquals(id, dto.id());
        assertEquals(1, dto.order());
        assertEquals(42.5, dto.weightKg());
        assertEquals(10, dto.reps());
        assertEquals(Boolean.TRUE, dto.isPersonalRecord());
    }

    @Test
    void sessionExerciseMapperMapsNestedCatalogAndSets() {
        SessionExerciseMapper mapper = Mappers.getMapper(SessionExerciseMapper.class);
        ReflectionTestUtils.setField(mapper, "setMapper", Mappers.getMapper(SetMapper.class));
        CatalogExercise catalogExercise = new CatalogExercise();
        UUID catalogId = UUID.randomUUID();
        catalogExercise.setId(catalogId);
        catalogExercise.setName("Supino");

        SessionExercise sessionExercise = new SessionExercise();
        UUID sessionExerciseId = UUID.randomUUID();
        sessionExercise.setId(sessionExerciseId);
        sessionExercise.setExercise(catalogExercise);
        sessionExercise.setOrder(2);

        com.beefit.api.domain.Set set = new com.beefit.api.domain.Set();
        set.setId(UUID.randomUUID());
        set.setOrder(1);
        set.setWeightKg(60.0);
        set.setReps(8);
        set.setIsPersonalRecord(Boolean.FALSE);
        sessionExercise.addSet(set);

        SessionExerciseResponseDTO dto = mapper.toDto(sessionExercise);

        assertEquals(sessionExerciseId, dto.id());
        assertEquals(catalogId, dto.catalogExerciseId());
        assertEquals("Supino", dto.catalogExerciseName());
        assertEquals(2, dto.order());
        assertEquals(1, dto.sets().size());
    }

    @Test
    void workoutSessionMapperMapsStatusMuscleGroupsAndExercises() {
        WorkoutSessionMapper mapper = Mappers.getMapper(WorkoutSessionMapper.class);
        SessionExerciseMapper sessionExerciseMapper = Mappers.getMapper(SessionExerciseMapper.class);
        ReflectionTestUtils.setField(sessionExerciseMapper, "setMapper", Mappers.getMapper(SetMapper.class));
        ReflectionTestUtils.setField(mapper, "workoutMuscleGroupMapper", new WorkoutMuscleGroupMapper());
        ReflectionTestUtils.setField(mapper, "sessionExerciseMapper", sessionExerciseMapper);
        WorkoutSession session = new WorkoutSession();
        UUID id = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 1, 12, 0);
        session.setId(id);
        session.setStatus(WorkoutStatus.IN_PROGRESS);
        session.setStartedAt(startedAt);
        session.setMuscleGroupsList(List.of(MuscleGroups.CHEST));
        session.setIsPrivate(Boolean.FALSE);
        session.setExercises(List.of());

        WorkoutSessionResponseDTO dto = mapper.toDto(session);

        assertEquals(id, dto.id());
        assertEquals("in_progress", dto.status());
        assertEquals(startedAt, dto.startedAt());
        assertEquals(List.of("peito"), dto.muscleGroups());
        assertEquals(Boolean.FALSE, dto.isPrivate());
    }
}
