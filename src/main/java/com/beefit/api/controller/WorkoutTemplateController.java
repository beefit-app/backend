package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.service.WorkoutTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/templates")
public class WorkoutTemplateController {

    private final WorkoutTemplateService WTService;

    @PostMapping
    // Lembrar de adicionar o id do amigo na sprint de Social
    public ResponseEntity<WorkoutTemplateResponseDTO> createTemplate(@RequestBody @Valid WorkoutTemplateRequestDTO workoutTemplateReqDTO,
                                                          @AuthenticationPrincipal User user /*, UUID friend */ ){

        WorkoutTemplateResponseDTO responseDTO = WTService.createTemplate(workoutTemplateReqDTO, user.getId());

        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping
    public ResponseEntity<List<WorkoutTemplateResponseDTO>> getUserTemplates(@AuthenticationPrincipal User user) {
        List<WorkoutTemplateResponseDTO> workoutTemplates = WTService.getUserTemplates(user.getId());

        return ResponseEntity.ok(workoutTemplates);
    }
}
