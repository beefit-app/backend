package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.comment.CommentResponseDTO;
import com.beefit.api.dto.comment.CreateCommentDTO;
import com.beefit.api.service.PostCommentService;
import com.beefit.api.service.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts/{postId}/comments")
public class PostCommentController {
    private final PostCommentService commentService;
    private final RateLimitService rateLimitService;

    @GetMapping
    public Page<CommentResponseDTO> list(@AuthenticationPrincipal User user, @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return commentService.list(user, postId, page, size);
    }

    @GetMapping("/count")
    public long count(@AuthenticationPrincipal User user, @PathVariable UUID postId) {
        return commentService.count(user, postId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponseDTO create(@AuthenticationPrincipal User user, @PathVariable UUID postId,
            @RequestBody @Valid CreateCommentDTO request) {
        rateLimitService.checkComment(user.getId());
        return commentService.create(user, postId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID postId, @PathVariable UUID commentId) {
        commentService.delete(user, postId, commentId);
    }
}
