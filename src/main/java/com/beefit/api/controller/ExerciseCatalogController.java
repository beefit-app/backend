package com.beefit.api.controller;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.repository.CatalogExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exercises")
public class ExerciseCatalogController {

    private final CatalogExerciseRepository catalogExerciseRepository;

    @GetMapping
    public ResponseEntity<Page<CatalogExercise>> listExercises(
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {

        // Se o usuário passar ?search=supino, filtra. Se não passar nada, traz todos paginados.
        Page<CatalogExercise> exercises = catalogExerciseRepository.findByNameContainingIgnoreCase(search, pageable);
        return ResponseEntity.ok(exercises);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogExercise> getExerciseById(@PathVariable UUID id) {
        return catalogExerciseRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
