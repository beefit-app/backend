package com.beefit.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.beefit.api.domain.User;
import com.beefit.api.dto.PostLikeDTO;
import com.beefit.api.service.PostService;

@RestController
@RequestMapping("api/posts/")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("{postId}/like")
    public ResponseEntity<PostLikeDTO> toggleLike(@AuthenticationPrincipal User user, @PathVariable UUID postId) {
        PostLikeDTO postLikeDTO = postService.toggleLike(user.getId(), postId);

        return ResponseEntity.ok(postLikeDTO);
    }
}
