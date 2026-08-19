package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutMedia;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.exception.media.MediaNotFoundException;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutMediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class WorkoutMediaServiceTest {

    @Mock
    private WorkoutMediaRepository mediaRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    private WorkoutMediaService service;
    private UUID mediaId;
    private User owner;
    private User viewer;
    private WorkoutMedia media;

    @BeforeEach
    void setUp() {
        service = new WorkoutMediaService(mediaRepository, postRepository, userRepository);
        mediaId = UUID.randomUUID();
        owner = user(UserRole.USER);
        viewer = user(UserRole.USER);
        media = new WorkoutMedia();
        media.setId(mediaId);
        media.setOwner(owner);
<<<<<<< HEAD
        org.mockito.Mockito.lenient().when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
=======
        lenient().when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
>>>>>>> origin/feat/app-refining
    }

    @Test
    void ownerCanReadAnUnassociatedUpload() {
        assertSame(media, service.getVisible(mediaId, owner));
        verify(userRepository, never()).existsByProfilePicture("/api/media/" + mediaId);
    }

    @Test
    void acceptedFriendCanReadAnAssignedVisibleProfilePicture() {
        when(userRepository.existsVisibleProfilePicture("/api/media/" + mediaId, viewer.getId())).thenReturn(true);
        assertSame(media, service.getVisible(mediaId, viewer));
    }

    @Test
    void unrelatedUserCannotReadAnAssignedProfilePicture() {
        assertThrows(MediaNotFoundException.class, () -> service.getVisible(mediaId, viewer));
    }

    @Test
    void acceptedFriendCanReadVisiblePostMedia() {
        when(postRepository.existsVisibleMedia(mediaId, viewer.getId())).thenReturn(true);
        assertSame(media, service.getVisible(mediaId, viewer));
    }

    @Test
    void adminCanReadOnlyMediaAssociatedWithAPost() {
        User admin = user(UserRole.ADMIN);
        when(postRepository.existsByPhotoMediaId(mediaId)).thenReturn(true);
        assertSame(media, service.getVisible(mediaId, admin));
    }

    @Test
    void unrelatedUserCannotReadMedia() {
        assertThrows(MediaNotFoundException.class, () -> service.getVisible(mediaId, viewer));
    }

    @Test
    void rejectsUploadWhenPerUserMediaQuotaIsReached() {
        when(mediaRepository.countByOwnerId(owner.getId())).thenReturn(100L);
        org.springframework.mock.web.MockMultipartFile upload = new org.springframework.mock.web.MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[] {1});
        assertThrows(IllegalArgumentException.class, () -> service.upload(owner, upload));
    }

    @Test
    void uploadSavesSanitizedPngSuccessfully() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        byte[] bytes = baos.toByteArray();

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "my-photo.png", "image/png", bytes);

        when(mediaRepository.countByOwnerId(owner.getId())).thenReturn(0L);
        when(mediaRepository.countByOwnerIdAndConsumedFalse(owner.getId())).thenReturn(0L);
        when(mediaRepository.save(org.mockito.ArgumentMatchers.any(WorkoutMedia.class)))
                .thenAnswer(invocation -> {
                    WorkoutMedia m = invocation.getArgument(0);
                    m.setId(UUID.randomUUID());
                    return m;
                });

        var response = service.upload(owner, file);
        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertNotNull(response.mediaId());
    }

    @Test
    void uploadSavesSanitizedJpegSuccessfully() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpeg", baos);
        byte[] bytes = baos.toByteArray();

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "../../../unsafe/path.jpg", "image/jpeg", bytes);

        when(mediaRepository.countByOwnerId(owner.getId())).thenReturn(0L);
        when(mediaRepository.countByOwnerIdAndConsumedFalse(owner.getId())).thenReturn(0L);
        when(mediaRepository.save(org.mockito.ArgumentMatchers.any(WorkoutMedia.class)))
                .thenAnswer(invocation -> {
                    WorkoutMedia m = invocation.getArgument(0);
                    m.setId(UUID.randomUUID());
                    org.junit.jupiter.api.Assertions.assertFalse(m.getFileName().contains("/"));
                    org.junit.jupiter.api.Assertions.assertTrue(m.getFileName().endsWith(".jpg"));
                    return m;
                });

        var response = service.upload(owner, file);
        org.junit.jupiter.api.Assertions.assertNotNull(response);
    }

    @Test
    void uploadThrowsWhenQuotaExceeded() {
        when(mediaRepository.countByOwnerId(owner.getId())).thenReturn(0L);
        when(mediaRepository.countByOwnerIdAndConsumedFalse(owner.getId())).thenReturn(15L);

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[100]);

        var ex = assertThrows(IllegalArgumentException.class, () -> service.upload(owner, file));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("limite de fotos pendentes"));
    }

    private User user(UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }
}

