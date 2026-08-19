package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutMedia;
import com.beefit.api.dto.media.WorkoutMediaResponseDTO;
import com.beefit.api.service.WorkoutMediaService;
import com.beefit.api.service.RateLimitService;
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
import java.time.Duration;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.CacheControl;

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
    public ResponseEntity<ByteArrayResource> get(@AuthenticationPrincipal User user, @PathVariable UUID mediaId) {
        WorkoutMedia media = workoutMediaService.getVisible(mediaId, user);
        String etag = etag(media.getContent());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .eTag(etag)
                .body(new ByteArrayResource(media.getContent()));
    }

    private String etag(byte[] content) {
        try {
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }
}
