package com.beefit.api.repository;

import com.beefit.api.domain.CatalogExercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CatalogExerciseRepository extends JpaRepository<CatalogExercise, UUID> {

    // Busca Paginada por Nome
    Page<CatalogExercise> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
