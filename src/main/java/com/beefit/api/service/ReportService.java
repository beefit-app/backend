package com.beefit.api.service;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.Report;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.domain.enums.ReportTargetType;
import com.beefit.api.dto.report.CreateReportDTO;
import com.beefit.api.dto.report.ReportResponseDTO;
import com.beefit.api.exception.report.CannotReportSelfException;
import com.beefit.api.exception.report.ReportAlreadyExistsException;
import com.beefit.api.exception.report.ReportTargetNotFoundException;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.ReportRepository;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    // Nomes dos indices parciais criados em V13; a violacao so pode ser traduzida
    // para "ja denunciou" se vier exatamente de um deles.
    private static final Set<String> DUPLICATE_REPORT_CONSTRAINTS =
            Set.of("reports_unique_post_per_reporter", "reports_unique_user_per_reporter");

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReportResponseDTO createReport(User reporter, CreateReportDTO dto) {
        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(dto.targetType());
        report.setReason(dto.reason());
        report.setDetails(normalizeDetails(dto.details()));
        report.setStatus(ReportStatus.PENDING);

        if (dto.targetType() == ReportTargetType.POST) {
            attachPostTarget(report, reporter, dto.targetId());
        } else {
            attachUserTarget(report, reporter, dto.targetId());
        }

        Report saved;
        try {
            saved = reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateReportViolation(exception)) {
                throw new ReportAlreadyExistsException();
            }
            throw exception;
        }

        return toResponse(saved);
    }

    private void attachPostTarget(Report report, User reporter, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(ReportTargetNotFoundException::new);

        // Post ja removido pela moderacao nao deve aceitar denuncia nova: o conteudo
        // nao esta mais visivel e a fila encheria de ruido sobre algo ja resolvido.
        if (Boolean.TRUE.equals(post.getIsRemoved())) {
            throw new ReportTargetNotFoundException();
        }
        if (post.getUser().getId().equals(reporter.getId())) {
            throw new CannotReportSelfException();
        }
        if (reportRepository.existsByReporterIdAndTargetPostId(reporter.getId(), postId)) {
            throw new ReportAlreadyExistsException();
        }

        report.setTargetPost(post);
    }

    private void attachUserTarget(Report report, User reporter, UUID userId) {
        if (userId.equals(reporter.getId())) {
            throw new CannotReportSelfException();
        }

        User target = userRepository.findById(userId).orElseThrow(ReportTargetNotFoundException::new);

        if (reportRepository.existsByReporterIdAndTargetUserId(reporter.getId(), userId)) {
            throw new ReportAlreadyExistsException();
        }

        report.setTargetUser(target);
    }

    private String normalizeDetails(String details) {
        if (details == null) {
            return null;
        }
        String trimmed = details.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isDuplicateReportViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && DUPLICATE_REPORT_CONSTRAINTS.contains(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ReportResponseDTO toResponse(Report report) {
        UUID targetId = report.getTargetType() == ReportTargetType.POST
                ? report.getTargetPost().getId()
                : report.getTargetUser().getId();

        return new ReportResponseDTO(
                report.getId(),
                report.getTargetType(),
                targetId,
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
