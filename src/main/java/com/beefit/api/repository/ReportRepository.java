package com.beefit.api.repository;

import com.beefit.api.domain.Report;
import com.beefit.api.domain.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetPostId(UUID reporterId, UUID targetPostId);

    boolean existsByReporterIdAndTargetUserId(UUID reporterId, UUID targetUserId);

    Page<Report> findAllByStatus(ReportStatus status, Pageable pageable);
}
