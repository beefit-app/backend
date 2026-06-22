package com.beefit.api.repository;

import com.beefit.api.domain.WorkoutMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutMediaRepository extends JpaRepository<WorkoutMedia, UUID> {
    Optional<WorkoutMedia> findByIdAndOwnerId(UUID id, UUID ownerId);
}
