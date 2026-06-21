package com.beefit.api.service;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import com.beefit.api.dto.feed.PostLikeDTO;
import com.beefit.api.exception.friendship.PostNotFoundException;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.repository.LikeRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private UserRepository userRepository;

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

        post = new Post();
        post.setId(postId);
        post.setUser(user);
        post.setTotalLikes(0);
        post.setIsRemoved(false);
    }

    @Test
    void toggleLikeCreatesLikeAndIncrementsCounterWhenPostWasNotLiked() {
        when(postRepository.findByIdAndIsRemovedFalse(postId)).thenReturn(Optional.of(post));
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
    }

    @Test
    void toggleLikeRemovesExistingLikeAndDecrementsCounter() {
        post.setTotalLikes(1);

        when(postRepository.findByIdAndIsRemovedFalse(postId)).thenReturn(Optional.of(post));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(likeRepository.existsByLikedByAndPost(user, post)).thenReturn(true);

        PostLikeDTO response = postService.toggleLike(userId, postId);

        assertFalse(response.liked());
        assertEquals(0, response.likes_count());
        assertEquals(0, post.getTotalLikes());
        verify(likeRepository).deleteByLikedByAndPost(user, post);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void toggleLikeThrowsWhenPostDoesNotExistOrWasRemoved() {
        when(postRepository.findByIdAndIsRemovedFalse(postId)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.toggleLike(userId, postId));

        verify(userRepository, never()).findById(any(UUID.class));
    }

    @Test
    void toggleLikeThrowsWhenUserDoesNotExist() {
        when(postRepository.findByIdAndIsRemovedFalse(postId)).thenReturn(Optional.of(post));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> postService.toggleLike(userId, postId));

        verify(likeRepository, never()).existsByLikedByAndPost(any(User.class), any(Post.class));
    }
}
