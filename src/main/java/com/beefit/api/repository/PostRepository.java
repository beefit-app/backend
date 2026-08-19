package com.beefit.api.repository;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    // Montagem do Feed Social global (colmeia)
    // Busca todas as publicações públicas não removidas.
    // Utiliza JOIN FETCH para carregar o dono do post e a sessão de treino de uma vez.
    @Query(value = "SELECT p FROM Post p " +
            "JOIN FETCH p.user u " +
            "JOIN FETCH p.workoutSession w " +
            "WHERE p.isRemoved = false AND (" +
            "  u = :currentUser OR " +
            "  (u.ghostMode = false AND w.isPrivate = false AND EXISTS (SELECT 1 FROM Friendship f WHERE " +
            "    f.status = com.beefit.api.domain.enums.FriendshipStatus.ACCEPTED AND (" +
            "      (f.requester = :currentUser AND f.addressee = u) OR " +
            "      (f.addressee = :currentUser AND f.requester = u)" +
            "    )" +
            "  ))" +
            ") " +
            "ORDER BY p.publishedAt DESC",
            // É obrigatório fornecer a countQuery quando usamos JOIN FETCH com paginação
            countQuery = "SELECT count(p) FROM Post p " +
                    "WHERE p.isRemoved = false AND (" +
                    "  p.user = :currentUser OR " +
                    "  (p.user.ghostMode = false AND p.workoutSession.isPrivate = false AND EXISTS (SELECT 1 FROM Friendship f WHERE " +
                    "    f.status = com.beefit.api.domain.enums.FriendshipStatus.ACCEPTED AND (" +
                    "      (f.requester = :currentUser AND f.addressee = p.user) OR " +
                    "      (f.addressee = :currentUser AND f.requester = p.user)" +
                    "    )" +
                    "  ))" +
                    ")")
    Page<Post> findFriendsFeed(@Param("currentUser") User currentUser, Pageable pageable);

    // Traz o post pelo ID, mas já filtrando para garantir que não foi removido pela moderação
    Optional<Post> findByIdAndIsRemovedFalse(UUID id);

    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH p.workoutSession w " +
            "WHERE p.id = :postId AND p.isRemoved = false AND (" +
            "u.id = :viewerId OR (u.ghostMode = false AND w.isPrivate = false AND EXISTS (" +
            "SELECT 1 FROM Friendship f WHERE f.status = com.beefit.api.domain.enums.FriendshipStatus.ACCEPTED AND (" +
            "(f.requester.id = :viewerId AND f.addressee = u) OR " +
            "(f.addressee.id = :viewerId AND f.requester = u)))))")
    Optional<Post> findVisibleById(@Param("postId") UUID postId, @Param("viewerId") UUID viewerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p JOIN FETCH p.user u JOIN FETCH p.workoutSession w " +
            "WHERE p.id = :postId AND p.isRemoved = false AND (" +
            "u.id = :viewerId OR (u.ghostMode = false AND w.isPrivate = false AND EXISTS (" +
            "SELECT 1 FROM Friendship f WHERE f.status = com.beefit.api.domain.enums.FriendshipStatus.ACCEPTED AND (" +
            "(f.requester.id = :viewerId AND f.addressee = u) OR " +
            "(f.addressee.id = :viewerId AND f.requester = u)))))")
    Optional<Post> findVisibleByIdForUpdate(@Param("postId") UUID postId, @Param("viewerId") UUID viewerId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Post p " +
            "JOIN p.user u JOIN p.workoutSession w " +
            "WHERE p.photoMediaId = :mediaId AND p.isRemoved = false AND w.isPrivate = false " +
            "AND u.ghostMode = false AND (u.id = :viewerId OR EXISTS (" +
            "SELECT 1 FROM Friendship f WHERE f.status = com.beefit.api.domain.enums.FriendshipStatus.ACCEPTED AND (" +
            "(f.requester.id = :viewerId AND f.addressee = u) OR " +
            "(f.addressee.id = :viewerId AND f.requester = u))))")
    boolean existsVisibleMedia(@Param("mediaId") UUID mediaId, @Param("viewerId") UUID viewerId);

    boolean existsByPhotoMediaId(UUID mediaId);

    // Lista paginada dos posts do usuário logado
    Page<Post> findAllByUserAndIsRemovedFalseOrderByPublishedAtDesc(User user, Pageable pageable);

    // Busca todos os posts para inspeção do Admin, independente de remoção
    Page<Post> findAllByOrderByPublishedAtDesc(Pageable pageable);

    // Executa a exclusão lógica (Soft Delete) marcando o post como removido
    @Modifying
    @Query("UPDATE Post p SET p.isRemoved = true WHERE p.id = :postId")
    void softDeletePost(@Param("postId") UUID postId);
}
