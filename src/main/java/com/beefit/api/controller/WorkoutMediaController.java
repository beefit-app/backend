package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutMedia;
import com.beefit.api.dto.media.WorkoutMediaResponseDTO;
import com.beefit.api.service.WorkoutMediaService;
import com.beefit.api.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class WorkoutMediaController {
    private final WorkoutMediaService workoutMediaService;
    private final RateLimitService rateLimitService;

    @PostMapping(value = "/workout-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkoutMediaResponseDTO> upload(@AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws IOException {
        rateLimitService.checkUpload(user.getId());
        return ResponseEntity.ok(workoutMediaService.upload(user, file));
    }

    @PostMapping(value = "/profile-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkoutMediaResponseDTO> uploadProfilePhoto(@AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws IOException {
        rateLimitService.checkUpload(user.getId());
        return ResponseEntity.ok(workoutMediaService.upload(user, file));
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<Void> get(@AuthenticationPrincipal User user, @PathVariable UUID mediaId) {
        WorkoutMedia media = workoutMediaService.getVisible(mediaId, user);
        String presignedUrl = workoutMediaService.getPresignedUrl(media);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(presignedUrl))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)).cachePrivate())
                .build();
    }
}
