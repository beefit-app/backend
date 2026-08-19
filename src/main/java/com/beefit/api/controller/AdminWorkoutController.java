package com.beefit.api.controller;

import com.beefit.api.dto.AdminPostResponseDTO;
import com.beefit.api.dto.UpdatePostMetadataDTO;
import com.beefit.api.service.WorkoutModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import jakarta.validation.Valid;


// Controlador administrativo para inspeção e moderação de treinos publicados.
// Rotas protegidas (acesso exclusivo para administradores da plataforma).
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWorkoutController {

    private final WorkoutModerationService moderationService;

    // Lista todos os posts de forma linear para auditoria administrativa
    @GetMapping
    public ResponseEntity<Page<AdminPostResponseDTO>> listAllPosts(
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(moderationService.listAllPosts(pageable));
    }

    // Endpoint de "exclusão lógica" para moderar posts indevidos
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        moderationService.removeInappropriatePost(id);

        // Retorna 204 No Content indicando que a ação de moderação foi concluída com sucesso
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}")
    public ResponseEntity<Void> editPost(@PathVariable UUID id, @Valid @RequestBody UpdatePostMetadataDTO dto) {
        moderationService.editPostMetadata(id, dto);
        return ResponseEntity.noContent().build();
    }
}
