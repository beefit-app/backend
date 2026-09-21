package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutMedia;
import com.beefit.api.dto.media.WorkoutMediaResponseDTO;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.exception.media.MediaNotFoundException;
import com.beefit.api.infra.cloudflare.R2StorageService;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.awt.Graphics2D;

@Service
@RequiredArgsConstructor
public class WorkoutMediaService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16_000_000;
    private static final long MAX_MEDIA_PER_USER = 100;
    private static final int MAX_PENDING_UPLOADS = 15;
    private final WorkoutMediaRepository repository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final R2StorageService r2StorageService;

    public WorkoutMediaResponseDTO upload(User user, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Envie uma imagem de até 10 MB.");
        }
        if (repository.countByOwnerId(user.getId()) >= MAX_MEDIA_PER_USER) {
            throw new IllegalArgumentException("Limite de mídias atingido. Exclua mídias antigas antes de enviar outra.");
        }
        if (repository.countByOwnerIdAndConsumedFalse(user.getId()) >= MAX_PENDING_UPLOADS) {
            throw new IllegalArgumentException("Você atingiu o limite de fotos pendentes. Finalize ou descarte as anteriores antes de novos envios.");
        }
        ProcessedImage processed = inspectAndNormalizeImage(file.getBytes());
        String ext = "png".equalsIgnoreCase(processed.format()) ? "png" : "jpg";
        String storageKey = "media/" + user.getId() + "/" + UUID.randomUUID() + "." + ext;
        r2StorageService.upload(storageKey, processed.content(), processed.contentType());

        WorkoutMedia media = new WorkoutMedia();
        media.setOwner(user);
        media.setStorageKey(storageKey);
        media.setContentType(processed.contentType());
        media.setFileName(sanitizeFileName(file.getOriginalFilename(), processed.format()));
        WorkoutMedia saved = repository.save(media);
        return new WorkoutMediaResponseDTO(saved.getId(), "/api/media/" + saved.getId());
    }

    public String getPresignedUrl(WorkoutMedia media) {
        return r2StorageService.generatePresignedUrl(media.getStorageKey());
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

    public void delete(UUID mediaId) {
        repository.findById(mediaId).ifPresent(media -> {
            r2StorageService.delete(media.getStorageKey());
            repository.delete(media);
        });
    }

    public void deleteAllForOwner(UUID userId) {
        List<WorkoutMedia> medias = repository.findByOwnerId(userId);
        for (WorkoutMedia media : medias) {
            r2StorageService.delete(media.getStorageKey());
        }
        repository.deleteByOwnerId(userId);
    }

    public void releasePreviousProfileMedia(String previousPath, String newPath, UUID userId) {
        if (previousPath == null || previousPath.equals(newPath) || !previousPath.startsWith("/api/media/")) return;
        try {
            UUID mediaId = UUID.fromString(previousPath.substring(previousPath.lastIndexOf('/') + 1));
            if (!postRepository.existsByPhotoMediaId(mediaId)) {
                repository.findByIdAndOwnerId(mediaId, userId).ifPresent(media -> {
                    r2StorageService.delete(media.getStorageKey());
                    repository.delete(media);
                });
            }
        } catch (IllegalArgumentException ignored) {
            // Referências legadas ou externas não pertencem ao armazenamento interno.
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupOrphanedMedia() {
        List<WorkoutMedia> orphaned = repository.findByConsumedFalseAndCreatedAtBefore(java.time.LocalDateTime.now().minusHours(24));
        for (WorkoutMedia media : orphaned) {
            r2StorageService.delete(media.getStorageKey());
        }
        repository.deleteAll(orphaned);
    }

    private record ProcessedImage(byte[] content, String contentType, String format) {}

    private ProcessedImage inspectAndNormalizeImage(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Envie uma imagem JPEG ou PNG válida.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw new IllegalArgumentException("São aceitas apenas imagens JPEG ou PNG.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || ((long) width * height) > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("A imagem excede as dimensões permitidas.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Imagem inválida.");

                String outputFormat = format.equals("png") ? "png" : "jpeg";
                BufferedImage normalized = image;
                if (outputFormat.equals("jpeg") && image.getColorModel().hasAlpha()) {
                    normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = normalized.createGraphics();
                    graphics.drawImage(image, 0, 0, null);
                    graphics.dispose();
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(normalized, outputFormat, output)) throw new IllegalArgumentException("Imagem inválida.");
                return new ProcessedImage(output.toByteArray(), outputFormat.equals("png") ? "image/png" : "image/jpeg", outputFormat);
            } finally {
                reader.dispose();
            }
        }
    }

    private String sanitizeFileName(String originalFilename, String format) {
        String ext = "png".equalsIgnoreCase(format) ? "png" : "jpg";
        if (originalFilename == null || originalFilename.isBlank()) {
            return "photo-" + UUID.randomUUID() + "." + ext;
        }
        String base = originalFilename.replaceAll("^.*[\\\\/]", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.isBlank() || base.equals(".")) {
            base = "photo-" + UUID.randomUUID();
        }
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }
        if (!base.toLowerCase(Locale.ROOT).endsWith("." + ext)) {
            base = base + "." + ext;
        }
        return base;
    }

    @Transactional(readOnly = true)
    public WorkoutMedia getVisible(UUID mediaId, User viewer) {
        WorkoutMedia media = repository.findById(mediaId).orElseThrow(MediaNotFoundException::new);
        if (media.getOwner().getId().equals(viewer.getId())) {
            return media;
        }

        String mediaPath = "/api/media/" + mediaId;
        if (viewer.getRole() == UserRole.ADMIN && userRepository.existsByProfilePicture(mediaPath)) {
            return media;
        }

        if (userRepository.existsVisibleProfilePicture(mediaPath, viewer.getId())) {
            return media;
        }

        if (viewer.getRole() == UserRole.ADMIN && postRepository.existsByPhotoMediaId(mediaId)) {
            return media;
        }

        if (postRepository.existsVisibleMedia(mediaId, viewer.getId())) {
            return media;
        }

        throw new MediaNotFoundException();
    }
}
