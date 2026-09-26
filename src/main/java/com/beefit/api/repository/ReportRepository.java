package com.beefit.api.repository;

import com.beefit.api.domain.Report;
import com.beefit.api.domain.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetPostId(UUID reporterId, UUID targetPostId);

    boolean existsByReporterIdAndTargetUserId(UUID reporterId, UUID targetUserId);

    Page<Report> findAllByStatus(ReportStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Report r SET r.status = :newStatus "
            + "WHERE r.targetPost.id = :postId AND r.status = com.beefit.api.domain.enums.ReportStatus.PENDING")
    int closePendingByTargetPost(@Param("postId") UUID postId, @Param("newStatus") ReportStatus newStatus);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Report r SET r.status = :newStatus "
            + "WHERE r.targetUser.id = :userId AND r.status = com.beefit.api.domain.enums.ReportStatus.PENDING")
    int closePendingByTargetUser(@Param("userId") UUID userId, @Param("newStatus") ReportStatus newStatus);
}
