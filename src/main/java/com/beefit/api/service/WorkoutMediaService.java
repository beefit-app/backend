package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutMedia;
import com.beefit.api.dto.media.WorkoutMediaResponseDTO;
import com.beefit.api.repository.WorkoutMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkoutMediaService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private final WorkoutMediaRepository repository;

    public WorkoutMediaResponseDTO upload(User user, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE || file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Envie uma imagem de até 10 MB.");
        }
        WorkoutMedia media = new WorkoutMedia();
        media.setOwner(user);
        media.setContent(file.getBytes());
        media.setContentType(file.getContentType());
        media.setFileName(file.getOriginalFilename() == null ? "workout-photo" : file.getOriginalFilename());
        WorkoutMedia saved = repository.save(media);
        return new WorkoutMediaResponseDTO(saved.getId(), "/api/media/" + saved.getId());
    }

    public WorkoutMedia consume(UUID mediaId, UUID userId) {
        WorkoutMedia media = repository.findByIdAndOwnerId(mediaId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Foto de treino não encontrada."));
        if (Boolean.TRUE.equals(media.getConsumed())) {
            throw new IllegalArgumentException("Esta foto já foi usada em outro treino.");
        }
        media.setConsumed(true);
        return repository.save(media);
    }

    public void consumeForProfile(UUID mediaId, UUID userId) {
        WorkoutMedia media = repository.findByIdAndOwnerId(mediaId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Foto de perfil não encontrada."));
        media.setConsumed(true);
        repository.save(media);
    }

    public void delete(UUID mediaId) { repository.deleteById(mediaId); }

    public void deleteAllForOwner(UUID userId) { repository.deleteByOwnerId(userId); }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupOrphanedMedia() {
        repository.deleteAll(repository.findByConsumedFalseAndCreatedAtBefore(java.time.LocalDateTime.now().minusHours(24)));
    }

    public WorkoutMedia get(UUID mediaId) {
        return repository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Foto de treino não encontrada."));
    }
}
