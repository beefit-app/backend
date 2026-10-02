package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.event.workout.WorkoutFinishedEvent;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;

@ExtendWith(MockitoExtension.class)
class WorkoutPostServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private WorkoutMediaService workoutMediaService;

    @InjectMocks
    private WorkoutPostService workoutPostService;

    private User user;
    private WorkoutSession session;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setGhostMode(false);

        session = new WorkoutSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
    }

    @Test
    void shouldNotPostWhenShouldPostWorkoutIsFalse() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                session.getId(), user.getId(), LocalDateTime.now(), 3600, true, "Treino", false, false, null);

        workoutPostService.processWorkoutPost(event);

        verify(postRepository, never()).save(any());
    }

    @Test
    void shouldNotPostWhenWorkoutIsPrivate() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                session.getId(), user.getId(), LocalDateTime.now(), 3600, true, "Treino", true, true, null);

        workoutPostService.processWorkoutPost(event);

        verify(postRepository, never()).save(any());
    }

    @Test
    void shouldNotPostWhenPostAlreadyExistsForWorkoutSession() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                session.getId(), user.getId(), LocalDateTime.now(), 3600, true, "Treino", true, false, null);

        when(postRepository.findByWorkoutSessionId(session.getId())).thenReturn(Optional.of(new Post()));

        workoutPostService.processWorkoutPost(event);

        verify(userRepository, never()).findById(any());
        verify(postRepository, never()).save(any());
    }

    @Test
    void shouldNotPostWhenUserIsInGhostMode() {
        user.setGhostMode(true);
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                session.getId(), user.getId(), LocalDateTime.now(), 3600, true, "Treino", true, false, null);

        when(postRepository.findByWorkoutSessionId(session.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        workoutPostService.processWorkoutPost(event);

        verify(postRepository, never()).save(any());
    }

    @Test
    void shouldCreatePostSuccessfullyWithoutMedia() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                session.getId(), user.getId(), LocalDateTime.now(), 3600, true, "Treino de Peito", true, false, null);

        when(postRepository.findByWorkoutSessionId(session.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        workoutPostService.processWorkoutPost(event);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        assertEquals("Treino de Peito", savedPost.getTitle());
        assertEquals(user, savedPost.getUser());
        assertEquals(session, savedPost.getWorkoutSession());
        verify(workoutMediaService, never()).consume(any(), any());
    }

    @Test
    void shouldCreatePostSuccessfullyWithPhotoMedia() {
        UUID mediaId = UUID.randomUUID();
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                session.getId(), user.getId(), LocalDateTime.now(), 3600, true, "Treino de Pernas", true, false, mediaId);

        when(postRepository.findByWorkoutSessionId(session.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        workoutPostService.processWorkoutPost(event);

        verify(workoutMediaService).consume(mediaId, user.getId());
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertEquals(mediaId, postCaptor.getValue().getPhotoMediaId());
    }
}
