package com.beefit.api.service;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.dto.feed.PostLikeDTO;
import com.beefit.api.dto.feed.PostLikeEventDTO;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.exception.friendship.PostNotFoundException;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.repository.LikeRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.FriendshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private com.beefit.api.repository.PostCommentRepository commentRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;


    @Mock
    private SseService sseService;

    @InjectMocks
    private PostService postService;

    private UUID userId;
    private UUID postId;
    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();

        user = new User();
        user.setId(userId);

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);

        post = new Post();
        post.setId(postId);
        post.setUser(user);
        post.setWorkoutSession(session);
        post.setTotalLikes(0);
        post.setIsRemoved(false);
    }

    @Test
    void toggleLikeCreatesLikeAndIncrementsCounterWhenPostWasNotLiked() {
        var audience = List.of(userId, UUID.randomUUID());
        when(postRepository.findVisibleAudienceIds(postId)).thenReturn(audience);
        when(postRepository.findVisibleByIdForUpdate(postId, userId)).thenReturn(Optional.of(post));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(likeRepository.existsByLikedByAndPost(user, post)).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostLikeDTO response = postService.toggleLike(userId, postId);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());

        Post savedPost = postCaptor.getValue();
        assertTrue(response.liked());
        assertEquals(1, response.likes_count());
        assertEquals(1, savedPost.getTotalLikes());
        assertEquals(1, savedPost.getLikes().size());
        assertEquals(user, savedPost.getLikes().get(0).getLikedBy());
        assertEquals(post, savedPost.getLikes().get(0).getPost());
        assertNotNull(savedPost.getLikes().get(0).getLikedAt());
        var event = new SocialEventDTO<>(SocialEvent.POST_LIKED, new PostLikeEventDTO(postId, 1));
        verify(sseService).broadcastToUsers(event, audience);
        verifyNoMoreInteractions(sseService);
    }

    @Test
    void toggleLikeRemovesExistingLikeAndDecrementsCounter() {
        post.setTotalLikes(1);
        when(postRepository.findVisibleAudienceIds(postId)).thenReturn(List.of(userId));

        when(postRepository.findVisibleByIdForUpdate(postId, userId)).thenReturn(Optional.of(post));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(likeRepository.existsByLikedByAndPost(user, post)).thenReturn(true);

        PostLikeDTO response = postService.toggleLike(userId, postId);

        assertFalse(response.liked());
        assertEquals(0, response.likes_count());
        assertEquals(0, post.getTotalLikes());
        verify(likeRepository).deleteByLikedByAndPost(user, post);
        verify(postRepository, never()).save(any(Post.class));
        verify(sseService).broadcastToUsers(new SocialEventDTO<>(SocialEvent.POST_LIKED, new PostLikeEventDTO(postId, 0)),
                List.of(userId));
    }

    @Test
    void toggleLikeThrowsWhenPostDoesNotExistOrWasRemoved() {
        when(postRepository.findVisibleByIdForUpdate(postId, userId)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.toggleLike(userId, postId));

        verify(userRepository, never()).findById(any(UUID.class));
        verifyNoInteractions(sseService);
    }

    @Test
    void toggleLikeThrowsWhenUserDoesNotExist() {
        when(postRepository.findVisibleByIdForUpdate(postId, userId)).thenReturn(Optional.of(post));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> postService.toggleLike(userId, postId));

        verify(likeRepository, never()).existsByLikedByAndPost(any(User.class), any(Post.class));
    }

    @Test
    void profilePostsReturnEmptyPageForUsersWhoAreNotAcceptedFriends() {
        User target = new User();
        target.setId(UUID.randomUUID());
        target.setGhostMode(false);
        when(userRepository.findByUsernameIgnoreCase("target")).thenReturn(Optional.of(target));
        when(friendshipRepository.existsAcceptedFriendshipBetween(userId, target.getId())).thenReturn(false);

        var result = postService.getPostsByUsername(user, "target", PageRequest.of(0, 10));
        assertTrue(result.isEmpty());
    }

    @Test
    void profilePostsThrowForGhostModeUser() {
        User target = new User();
        target.setId(UUID.randomUUID());
        target.setGhostMode(true);
        when(userRepository.findByUsernameIgnoreCase("target")).thenReturn(Optional.of(target));

        assertThrows(UserNotFoundException.class,
                () -> postService.getPostsByUsername(user, "target", PageRequest.of(0, 10)));
    }
}
