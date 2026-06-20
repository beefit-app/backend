package com.beefit.api.service;

import com.beefit.api.domain.Post;
import com.beefit.api.dto.UpdatePostMetadataDTO;
import com.beefit.api.exception.PostNotFoundException;
import com.beefit.api.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Serviço responsável por gerenciar a moderação de conteúdo gerado pelos usuários. Focado na remoção de posts ofensivos ou com métricas fraudulentas.
@Service
@RequiredArgsConstructor
public class WorkoutModerationService {

    private final PostRepository postRepository;

    @Transactional
    public void removeInappropriatePost(UUID postId) {
        // Verifica se o post existe antes de processar a remoção para evitar erros
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException();
        }

        // Aplica o Soft Delete no banco de dados via repositório
        postRepository.softDeletePost(postId);
    }
    @Transactional
    public void editPostMetadata(UUID postId, UpdatePostMetadataDTO dto) {
        // Busca o post no repositório; se não achar, dispara a exceção existente
        Post post = postRepository.findById(postId)
                .orElseThrow(PostNotFoundException::new);

        // Atualiza os metadados da sessão de treino vinculada ao post
        var session = post.getWorkoutSession();
        if (dto.newTitle() != null) {
            session.setTitle(dto.newTitle());
        }
        // Exemplo: se houver campo de observação na sessão, atualize aqui
        // session.setObservation(dto.observation());

        postRepository.save(post);
    }
}