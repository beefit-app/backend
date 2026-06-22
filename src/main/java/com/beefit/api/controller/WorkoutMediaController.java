package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutMedia;
import com.beefit.api.dto.media.WorkoutMediaResponseDTO;
import com.beefit.api.service.WorkoutMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class WorkoutMediaController {
    private final WorkoutMediaService workoutMediaService;

    @PostMapping(value = "/workout-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkoutMediaResponseDTO> upload(@AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(workoutMediaService.upload(user, file));
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<ByteArrayResource> get(@PathVariable UUID mediaId) {
        WorkoutMedia media = workoutMediaService.get(mediaId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.getContentType()))
                .body(new ByteArrayResource(media.getContent()));
    }
}
