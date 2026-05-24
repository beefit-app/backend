package com.beefit.api.repository;

import com.beefit.api.domain.CatalogExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CatalogExerciseRepository extends JpaRepository<CatalogExercise, UUID> {
}
