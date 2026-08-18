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
        lenient().when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
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
        MockMultipartFile upload = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1});
        assertThrows(IllegalArgumentException.class, () -> service.upload(owner, upload));
    }

    private User user(UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }
}
