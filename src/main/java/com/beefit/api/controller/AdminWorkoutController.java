package com.beefit.api.controller;

import com.beefit.api.domain.Post;
import com.beefit.api.dto.UpdatePostMetadataDTO;
import com.beefit.api.service.WorkoutModerationService;
import com.beefit.api.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


// Controlador administrativo para inspeção e moderação de treinos publicados.
// Rotas protegidas (acesso exclusivo para administradores da plataforma).
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminWorkoutController {

    private final PostRepository postRepository;
    private final WorkoutModerationService moderationService;

    // Lista todos os posts de forma linear para auditoria administrativa
    @GetMapping
    public ResponseEntity<Page<Post>> listAllPosts(
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(postRepository.findAllByOrderByPublishedAtDesc(pageable));
    }

    // Endpoint de "exclusão lógica" para moderar posts indevidos
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        moderationService.removeInappropriatePost(id);

        // Retorna 204 No Content indicando que a ação de moderação foi concluída com sucesso
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}")
    public ResponseEntity<Void> editPost(@PathVariable UUID id, @RequestBody UpdatePostMetadataDTO dto) {
        moderationService.editPostMetadata(id, dto);
        return ResponseEntity.noContent().build();
    }
}