package com.beefit.api.repository;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    // Montagem do Feed Social
    // Busca as publicações dos amigos com status ACCEPTED.
    // Utiliza JOIN FETCH para carregar o dono do post e a sessão de treino de uma
    // vez,
    @Query(value = "SELECT p FROM Post p " +
            "JOIN FETCH p.user " +
            "JOIN FETCH p.workoutSession " +
            "WHERE p.isRemoved = false " +
            "  AND p.user IN (" +
            "      SELECT CASE WHEN f.requester = :currentUser THEN f.addressee ELSE f.requester END " +
            "      FROM Friendship f " +
            "      WHERE (f.requester = :currentUser OR f.addressee = :currentUser) " +
            "        AND f.status = 'ACCEPTED'" +
            "  )",
            // É obrigatório fornecer a countQuery quando usamos JOIN FETCH com paginação
            countQuery = "SELECT count(p) FROM Post p " +
                    "WHERE p.isRemoved = false " +
                    "  AND p.user IN (" +
                    "      SELECT CASE WHEN f.requester = :currentUser THEN f.addressee ELSE f.requester END " +
                    "      FROM Friendship f " +
                    "      WHERE (f.requester = :currentUser OR f.addressee = :currentUser) " +
                    "        AND f.status = 'ACCEPTED'" +
                    "  )")
    Page<Post> findFriendsFeed(@Param("currentUser") User currentUser, Pageable pageable);

    // Traz o post pelo ID, mas já filtrando para garantir que não foi removido pela
    // moderação
    Optional<Post> findByIdAndIsRemovedFalse(UUID id);

    // Lista páginada dos posts do usuário logado
    Page<Post> findAllByUserAndIsRemovedFalseOrderByPublishedAtDesc(User user, Pageable pageable);
}
