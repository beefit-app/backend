package com.beefit.api.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.beefit.api.domain.Like;
import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import com.beefit.api.dto.PostLikeDTO;
import com.beefit.api.exception.PostNotFoundException;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.repository.LikeRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final UserRepository userRepository;

    public PostService(PostRepository postRepository, LikeRepository likeRepository, UserRepository userRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PostLikeDTO toggleLike(UUID userId, UUID postId) {
        Post post = postRepository.findByIdAndIsRemovedFalse(postId).orElseThrow(PostNotFoundException::new);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        Boolean isPostLikedAlready = likeRepository.existsByLikedByAndPost(user, post);

        if (isPostLikedAlready) {
            post.decrementLikes();
            likeRepository.deleteByLikedByAndPost(user, post);
        } else {
            Like like = new Like();
            like.setLikedBy(user);
            like.setPost(post);
            like.setLikedAt(LocalDateTime.now());

            post.incrementLikes();
            post.getLikes().add(like);

            postRepository.save(post);
        }

        // retorna o contrário da variável de post já curtido pois o valor foi invertido
        // após o processamento da função
        return new PostLikeDTO(!isPostLikedAlready, post.getTotalLikes());
    }
}
