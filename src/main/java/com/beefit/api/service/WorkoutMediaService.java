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
import java.io.ByteArrayOutputStream;
import java.awt.Graphics2D;

@Service
@RequiredArgsConstructor
public class WorkoutMediaService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16_000_000;
    private static final long MAX_MEDIA_PER_USER = 100;
    private final WorkoutMediaRepository repository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public WorkoutMediaResponseDTO upload(User user, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Envie uma imagem de até 10 MB.");
        }
        if (repository.countByOwnerId(user.getId()) >= MAX_MEDIA_PER_USER) {
            throw new IllegalArgumentException("Limite de mídias atingido. Exclua mídias antigas antes de enviar outra.");
        }
        ProcessedImage processed = inspectAndNormalizeImage(file.getBytes());
        byte[] content = processed.content();
        String contentType = processed.contentType();
        WorkoutMedia media = new WorkoutMedia();
        media.setOwner(user);
        media.setContent(content);
        media.setContentType(contentType);
        media.setFileName(contentType.equals("image/png") ? "image.png" : "image.jpg");
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

    public void releasePreviousProfileMedia(String previousPath, String newPath, UUID userId) {
        if (previousPath == null || previousPath.equals(newPath) || !previousPath.startsWith("/api/media/")) return;
        try {
            UUID mediaId = UUID.fromString(previousPath.substring(previousPath.lastIndexOf('/') + 1));
            if (!postRepository.existsByPhotoMediaId(mediaId)) {
                repository.findByIdAndOwnerId(mediaId, userId).ifPresent(repository::delete);
            }
        } catch (IllegalArgumentException ignored) {
            // Referências legadas ou externas não pertencem ao armazenamento interno.
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupOrphanedMedia() {
        repository.deleteAll(repository.findByConsumedFalseAndCreatedAtBefore(java.time.LocalDateTime.now().minusHours(24)));
    }

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
                return new ProcessedImage(output.toByteArray(), outputFormat.equals("png") ? "image/png" : "image/jpeg");
            } finally {
                reader.dispose();
            }
        }
    }

    private record ProcessedImage(byte[] content, String contentType) {}

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
