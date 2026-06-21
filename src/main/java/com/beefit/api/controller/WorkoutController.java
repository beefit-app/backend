package com.beefit.api.controller;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.beefit.api.domain.User;
import com.beefit.api.dto.workout.FinishWorkoutSnapshotRequestDTO;
import com.beefit.api.dto.workout.StartWorkoutRequestDTO;
import com.beefit.api.dto.workout.WorkoutSessionResponseDTO;
import com.beefit.api.service.WorkoutSessionService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@AllArgsConstructor
@RequestMapping("/api/workout")
public class WorkoutController implements GenericController {
    private final WorkoutSessionService workoutSessionService;

    @GetMapping("/active")
    public ResponseEntity<WorkoutSessionResponseDTO> getActiveWorkout(@AuthenticationPrincipal User user) {
        Optional<WorkoutSessionResponseDTO> response = workoutSessionService.getActiveWorkout(user.getId());
        return response.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping("/start")
    public ResponseEntity<WorkoutSessionResponseDTO> startWorkout(@AuthenticationPrincipal User user,
            @Valid @RequestBody StartWorkoutRequestDTO request) {
        WorkoutSessionResponseDTO response = workoutSessionService.startWorkout(user.getId(), request);
        URI location = gerarLocation(response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{workoutId}/finish")
    public ResponseEntity<WorkoutSessionResponseDTO> finishWorkout(@AuthenticationPrincipal User user,
            @PathVariable UUID workoutId,
            @Valid @RequestBody FinishWorkoutSnapshotRequestDTO request) {
        WorkoutSessionResponseDTO response = workoutSessionService.finishWorkout(user.getId(), workoutId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{workoutId}/cancel")
    public ResponseEntity<WorkoutSessionResponseDTO> cancelWorkout(@AuthenticationPrincipal User user,
            @PathVariable UUID workoutId) {
        WorkoutSessionResponseDTO response = workoutSessionService.cancelWorkout(user.getId(), workoutId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{workoutId}/muscle-groups/{muscleGroup}")
    public ResponseEntity<WorkoutSessionResponseDTO> addMuscleGroup(@AuthenticationPrincipal User user,
            @PathVariable UUID workoutId,
            @PathVariable String muscleGroup) {
        WorkoutSessionResponseDTO response = workoutSessionService.addMuscleGroup(user.getId(), workoutId, muscleGroup);
        return ResponseEntity.ok(response);
    }
}
