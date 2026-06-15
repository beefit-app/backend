package com.beefit.api.controller;

import java.util.UUID;

// Imports para suportar a paginação do feed
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.beefit.api.domain.User;
import com.beefit.api.dto.feed.FeedPostResponseDTO;
import com.beefit.api.dto.feed.PostLikeDTO;
import com.beefit.api.service.PostService;

@RestController
@RequestMapping("api/posts/")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }


     // GET api/posts/feed
     // Retorna a linha do tempo social do usuário combinando suas publicações e as de seus amigos aceitos.
     // @param user Usuário autenticado pelo Spring Security.
     // @param pageable Define a paginação padrão (10 itens por página, ordenados pelos mais recentes).

    @GetMapping("feed")
    public ResponseEntity<Page<FeedPostResponseDTO>> getFriendsFeed(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<FeedPostResponseDTO> feed = postService.getFriendsFeed(user, pageable);
        return ResponseEntity.ok(feed);
    }

    @PostMapping("{postId}/like")
    public ResponseEntity<PostLikeDTO> toggleLike(@AuthenticationPrincipal User user, @PathVariable UUID postId) {
        PostLikeDTO postLikeDTO = postService.toggleLike(user.getId(), postId);

        return ResponseEntity.ok(postLikeDTO);
    }
}