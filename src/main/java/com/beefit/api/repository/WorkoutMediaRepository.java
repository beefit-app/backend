package com.beefit.api.repository;

import com.beefit.api.domain.WorkoutMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;

public interface WorkoutMediaRepository extends JpaRepository<WorkoutMedia, UUID> {
    Optional<WorkoutMedia> findByIdAndOwnerId(UUID id, UUID ownerId);
    List<WorkoutMedia> findByConsumedFalseAndCreatedAtBefore(LocalDateTime cutoff);
    void deleteByOwnerId(UUID ownerId);
    long countByOwnerId(UUID ownerId);
}
