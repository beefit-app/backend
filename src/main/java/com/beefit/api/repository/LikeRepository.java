package com.beefit.api.repository;

import com.beefit.api.domain.Like;
import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LikeRepository extends JpaRepository<Like, UUID> {

    // Retorna true se a curtida já existe (Ideal para a validação no Service)
    boolean existsByLikedByAndPost(User user, Post post);

    // Remove a curtida diretamente usando as referências de usuário e post
    void deleteByLikedByAndPost(User user, Post post);

    // Retorna o total de curtidas de um post específico
    int countByPost(Post post);
}