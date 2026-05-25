package com.beefit.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.beefit.api.domain.User;
import com.beefit.api.dto.StartWorkoutRequestDTO;
import com.beefit.api.service.WorkoutSessionService;

import lombok.AllArgsConstructor;

@RestController
@AllArgsConstructor
@RequestMapping("/api/workout")
public class WorkoutController {
    private final WorkoutSessionService workoutSessionService;

    @PostMapping("/start")
    public ResponseEntity<Void> startWorkout(@AuthenticationPrincipal User user,
            @RequestBody StartWorkoutRequestDTO request) {
        workoutSessionService.startWorkout(user.getId(), request);
        return ResponseEntity.noContent().build();

    }
}
