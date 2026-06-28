package com.beefit.api.repository;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    // Montagem do Feed Social global (colmeia)
    // Busca todas as publicações públicas não removidas.
    // Utiliza JOIN FETCH para carregar o dono do post e a sessão de treino de uma vez.
    @Query(value = "SELECT p FROM Post p " +
            "JOIN FETCH p.user " +
            "JOIN FETCH p.workoutSession " +
            "WHERE p.isRemoved = false " +
            "ORDER BY p.publishedAt DESC",
            // É obrigatório fornecer a countQuery quando usamos JOIN FETCH com paginação
            countQuery = "SELECT count(p) FROM Post p " +
                    "WHERE p.isRemoved = false")
    Page<Post> findFriendsFeed(@Param("currentUser") User currentUser, Pageable pageable);

    // Traz o post pelo ID, mas já filtrando para garantir que não foi removido pela moderação
    Optional<Post> findByIdAndIsRemovedFalse(UUID id);

    // Lista paginada dos posts do usuário logado
    Page<Post> findAllByUserAndIsRemovedFalseOrderByPublishedAtDesc(User user, Pageable pageable);

    // Busca todos os posts para inspeção do Admin, independente de remoção
    Page<Post> findAllByOrderByPublishedAtDesc(Pageable pageable);

    // Executa a exclusão lógica (Soft Delete) marcando o post como removido
    @Modifying
    @Query("UPDATE Post p SET p.isRemoved = true WHERE p.id = :postId")
    void softDeletePost(@Param("postId") UUID postId);
}
