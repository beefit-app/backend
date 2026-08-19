package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutMedia;
import com.beefit.api.dto.media.WorkoutMediaResponseDTO;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.exception.media.MediaNotFoundException;
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
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkoutMediaService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16_000_000;
    private static final int MAX_PENDING_UPLOADS = 15;
    private final WorkoutMediaRepository repository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public WorkoutMediaResponseDTO upload(User user, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Envie uma imagem de até 10 MB.");
        }
        long pendingCount = repository.countByOwnerIdAndConsumedFalse(user.getId());
        if (pendingCount >= MAX_PENDING_UPLOADS) {
            throw new IllegalArgumentException("Você atingiu o limite de fotos pendentes. Finalize ou descarte as anteriores antes de novos envios.");
        }
        CleanImageData cleanImage = processAndSanitizeImage(file.getBytes());
        WorkoutMedia media = new WorkoutMedia();
        media.setOwner(user);
        media.setContent(cleanImage.content());
        media.setContentType(cleanImage.contentType());
        media.setFileName(sanitizeFileName(file.getOriginalFilename(), cleanImage.format()));
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

    private record CleanImageData(byte[] content, String contentType, String format) {}

    private CleanImageData processAndSanitizeImage(byte[] rawContent) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(rawContent))) {
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

                boolean isPng = format.equals("png");
                String canonicalFormat = isPng ? "png" : "jpeg";
                String contentType = isPng ? "image/png" : "image/jpeg";

                java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                if (isPng) {
                    ImageIO.write(image, "png", outputStream);
                } else {
                    BufferedImage rgbImage;
                    if (image.getType() == BufferedImage.TYPE_INT_RGB || image.getType() == BufferedImage.TYPE_3BYTE_BGR) {
                        rgbImage = image;
                    } else {
                        rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                        var g = rgbImage.createGraphics();
                        g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
                        g.dispose();
                    }
                    ImageIO.write(rgbImage, "jpeg", outputStream);
                }
                return new CleanImageData(outputStream.toByteArray(), contentType, canonicalFormat);
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
        if (userRepository.existsByProfilePicture(mediaPath)) {
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
