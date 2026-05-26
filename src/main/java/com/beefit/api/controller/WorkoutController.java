package com.beefit.api.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.beefit.api.domain.User;
import com.beefit.api.dto.StartWorkoutRequestDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;
import com.beefit.api.service.WorkoutSessionService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@AllArgsConstructor
@RequestMapping("/api/workout")
public class WorkoutController implements GenericController {
    private final WorkoutSessionService workoutSessionService;

    @PostMapping("/start")
    public ResponseEntity<WorkoutSessionResponseDTO> startWorkout(@AuthenticationPrincipal User user,
            @Valid @RequestBody StartWorkoutRequestDTO request) {
        WorkoutSessionResponseDTO response = workoutSessionService.startWorkout(user.getId(), request);
        URI location = gerarLocation(response.id());
        return ResponseEntity.created(location).body(response);
    }
}
