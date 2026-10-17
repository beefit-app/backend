package com.beefit.api.repository;

import com.beefit.api.domain.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    @EntityGraph(attributePaths = "author")
    Page<PostComment> findByPostIdAndIsRemovedFalse(UUID postId, Pageable pageable);

    Optional<PostComment> findByIdAndPostIdAndIsRemovedFalse(UUID id, UUID postId);

    long countByPostIdAndIsRemovedFalse(UUID postId);

    interface PostCount {
        UUID getPostId();
        long getTotal();
    }

    @Query("SELECT c.post.id AS postId, COUNT(c) AS total FROM PostComment c WHERE c.post.id IN :postIds AND c.isRemoved = false GROUP BY c.post.id")
    List<PostCount> countByPosts(Collection<UUID> postIds);
}
