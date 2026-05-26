package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.service.WorkoutTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class WorkoutTemplateController {

    @Autowired
    private WorkoutTemplateService WTService;

    @PostMapping
    public ResponseEntity<WorkoutTemplateResponseDTO> createTemplate(@RequestBody WorkoutTemplateRequestDTO workoutTemplateReqDTO,
                                                          @AuthenticationPrincipal User user){

        WorkoutTemplateResponseDTO responseDTO = WTService.createTemplate(workoutTemplateReqDTO, user.getId());

        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping
    public ResponseEntity<List<WorkoutTemplateResponseDTO>> getUserTemplates(@AuthenticationPrincipal User user) {
        List<WorkoutTemplateResponseDTO> workoutTemplates = WTService.getUserTemplates(user.getId());

        if(workoutTemplates.isEmpty()) return ResponseEntity.noContent().build();

        return ResponseEntity.ok(workoutTemplates);
    }
}
