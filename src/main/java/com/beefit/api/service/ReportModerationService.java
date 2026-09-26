package com.beefit.api.service;

import com.beefit.api.domain.Report;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.ReportResolutionAction;
import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.domain.enums.ReportTargetType;
import com.beefit.api.dto.report.AdminReportResponseDTO;
import com.beefit.api.dto.report.ResolveReportDTO;
import com.beefit.api.dto.report.ResolvedReportResponseDTO;
import com.beefit.api.exception.report.ReportAlreadyResolvedException;
import com.beefit.api.exception.report.ReportNotFoundException;
import com.beefit.api.repository.ReportRepository;
import com.beefit.api.utils.log.LogAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Fila de denúncias da equipe de moderação. As decisões reaproveitam a remoção de post
// e o banimento que o painel já usa, para que valham as mesmas regras e a mesma auditoria.
@Service
@RequiredArgsConstructor
public class ReportModerationService {

    private final ReportRepository reportRepository;
    private final WorkoutModerationService workoutModerationService;
    private final AdminUserService adminUserService;

    @Transactional(readOnly = true)
    public Page<AdminReportResponseDTO> listReports(ReportStatus status, Pageable pageable) {
        Page<Report> reports = status == null
                ? reportRepository.findAll(pageable)
                : reportRepository.findAllByStatus(status, pageable);

        return reports.map(this::toAdminReportResponse);
    }

    @Transactional
    @LogAudit(action = "RESOLVE_REPORT", description = "Decide uma denúncia de conteúdo.")
    public ResolvedReportResponseDTO resolveReport(UUID adminId, UUID reportId, ResolveReportDTO request) {
        Report report = reportRepository.findById(reportId).orElseThrow(ReportNotFoundException::new);

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ReportAlreadyResolvedException();
        }

        boolean isPostTarget = report.getTargetType() == ReportTargetType.POST;
        ReportResolutionAction action = request.action();

        switch (action) {
            case REMOVE_POST -> {
                if (!isPostTarget) {
                    throw new IllegalArgumentException("Só é possível remover publicação em denúncia de publicação.");
                }
                workoutModerationService.removeInappropriatePost(report.getTargetPost().getId());
            }
            case BAN_USER -> adminUserService.banUserForDays(adminId, targetOwner(report).getId(), request.banDays());
            case DISMISS -> { }
        }

        ReportStatus newStatus = action == ReportResolutionAction.DISMISS ? ReportStatus.DISMISSED : ReportStatus.REVIEWED;
        int closedReports = isPostTarget
                ? reportRepository.closePendingByTargetPost(report.getTargetPost().getId(), newStatus)
                : reportRepository.closePendingByTargetUser(report.getTargetUser().getId(), newStatus);

        return new ResolvedReportResponseDTO(report.getId(), action, newStatus, closedReports);
    }

    private User targetOwner(Report report) {
        return report.getTargetType() == ReportTargetType.POST
                ? report.getTargetPost().getUser()
                : report.getTargetUser();
    }

    private AdminReportResponseDTO toAdminReportResponse(Report report) {
        boolean isPostTarget = report.getTargetType() == ReportTargetType.POST;

        UUID targetId = isPostTarget ? report.getTargetPost().getId() : report.getTargetUser().getId();
        String targetPostTitle = isPostTarget ? report.getTargetPost().getTitle() : null;
        User targetOwner = targetOwner(report);
        User reporter = report.getReporter();

        return new AdminReportResponseDTO(
                report.getId(),
                reporter.getId(),
                reporter.getUsername(),
                report.getTargetType(),
                targetId,
                targetPostTitle,
                targetOwner.getId(),
                targetOwner.getUsername(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
