package com.beefit.api.service;

import com.beefit.api.domain.Report;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.domain.enums.ReportTargetType;
import com.beefit.api.dto.report.AdminReportResponseDTO;
import com.beefit.api.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Consulta da fila de denúncias pela equipe de moderação. Ações que mudem o estado
// da denúncia devem ser anotadas com @LogAudit, como em WorkoutModerationService.
@Service
@RequiredArgsConstructor
public class ReportModerationService {

    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public Page<AdminReportResponseDTO> listReports(ReportStatus status, Pageable pageable) {
        Page<Report> reports = status == null
                ? reportRepository.findAll(pageable)
                : reportRepository.findAllByStatus(status, pageable);

        return reports.map(this::toAdminReportResponse);
    }

    private AdminReportResponseDTO toAdminReportResponse(Report report) {
        boolean isPostTarget = report.getTargetType() == ReportTargetType.POST;

        UUID targetId = isPostTarget ? report.getTargetPost().getId() : report.getTargetUser().getId();
        User targetOwner = isPostTarget ? report.getTargetPost().getUser() : report.getTargetUser();
        User reporter = report.getReporter();

        return new AdminReportResponseDTO(
                report.getId(),
                reporter.getId(),
                reporter.getUsername(),
                report.getTargetType(),
                targetId,
                targetOwner.getId(),
                targetOwner.getUsername(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
