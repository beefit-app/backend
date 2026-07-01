package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.workout.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.workout.WorkoutTemplateResponseDTO;
import com.beefit.api.service.WorkoutTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @PutMapping("/{templateId}")
    public ResponseEntity<WorkoutTemplateResponseDTO> updateTemplate(
            @PathVariable UUID templateId,
            @RequestBody @Valid WorkoutTemplateRequestDTO workoutTemplateReqDTO,
            @AuthenticationPrincipal User user
    ) {
        WorkoutTemplateResponseDTO responseDTO = WTService.updateTemplate(templateId, workoutTemplateReqDTO, user.getId());

        return ResponseEntity.ok(responseDTO);
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable UUID templateId,
            @AuthenticationPrincipal User user
    ) {
        WTService.deleteTemplate(templateId, user.getId());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import/{postId}")
    public ResponseEntity<WorkoutTemplateResponseDTO> importTemplateFromPost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User usuarioAutenticado
    ) {
        WorkoutTemplateResponseDTO responseDTO = WTService.importTemplateFromPost(
                postId,
                usuarioAutenticado.getId()
        );

        return ResponseEntity.ok(responseDTO);
    }
}
