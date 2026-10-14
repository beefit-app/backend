package com.beefit.api.service;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.dto.exercise.CatalogExerciseResponseDTO;
import com.beefit.api.exception.exercises.CatalogExerciseNotFound;
import com.beefit.api.repository.CatalogExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExerciseCatalogService {
    private final CatalogExerciseRepository repository;

    @Transactional(readOnly = true)
    public Page<CatalogExerciseResponseDTO> list(String search, Pageable pageable) {
        String safeSearch = search == null ? "" : search.trim();
        if (safeSearch.length() > 100) throw new IllegalArgumentException("A busca deve ter no máximo 100 caracteres.");
        return repository.findByNameContainingIgnoreCase(safeSearch, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public CatalogExerciseResponseDTO get(UUID id) {
        return repository.findById(id).map(this::toDto).orElseThrow(CatalogExerciseNotFound::new);
    }

    private CatalogExerciseResponseDTO toDto(CatalogExercise exercise) {
        return new CatalogExerciseResponseDTO(exercise.getId(), exercise.getName(),
                exercise.getPrimaryMuscleGroupsList(), exercise.getSecondaryMuscleGroupsList(),
                exercise.getPrimaryMusclesList(), exercise.getSecondaryMusclesList(), exercise.getInstructions(),
                exercise.getImages());
    }
}
