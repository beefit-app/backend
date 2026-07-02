package com.beefit.api.repository;

import com.beefit.api.domain.WorkoutTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkoutTemplateRepository extends JpaRepository<WorkoutTemplate, UUID> {

    List<WorkoutTemplate> findByUserId(UUID id);
}
