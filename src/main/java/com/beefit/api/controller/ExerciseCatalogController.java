package com.beefit.api.controller;

import com.beefit.api.dto.exercise.CatalogExerciseResponseDTO;
import com.beefit.api.service.ExerciseCatalogService;
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

    private final ExerciseCatalogService exerciseCatalogService;

    @GetMapping
    public ResponseEntity<Page<CatalogExerciseResponseDTO>> listExercises(
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {

        // Se o usuário passar ?search=supino, filtra. Se não passar nada, traz todos paginados.
        Page<CatalogExerciseResponseDTO> exercises = exerciseCatalogService.list(search, pageable);
        return ResponseEntity.ok(exercises);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogExerciseResponseDTO> getExerciseById(@PathVariable UUID id) {
        return ResponseEntity.ok(exerciseCatalogService.get(id));
    }
}
