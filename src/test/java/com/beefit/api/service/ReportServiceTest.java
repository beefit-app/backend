package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.Report;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.ReportReason;
import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.domain.enums.ReportTargetType;
import com.beefit.api.dto.report.CreateReportDTO;
import com.beefit.api.exception.report.CannotReportSelfException;
import com.beefit.api.exception.report.ReportAlreadyExistsException;
import com.beefit.api.exception.report.ReportTargetNotFoundException;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.ReportRepository;
import com.beefit.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportService reportService;

    private User reporter;
    private User targetUser;
    private Post targetPost;

    @BeforeEach
    void setUp() {
        reporter = new User();
        reporter.setId(UUID.randomUUID());
        reporter.setUsername("reporter");

        targetUser = new User();
        targetUser.setId(UUID.randomUUID());
        targetUser.setUsername("target");

        targetPost = new Post();
        targetPost.setId(UUID.randomUUID());
        targetPost.setUser(targetUser);
        targetPost.setIsRemoved(false);
    }

    private CreateReportDTO postReport(UUID targetId) {
        return new CreateReportDTO(ReportTargetType.POST, targetId, ReportReason.SPAM, "  conteudo repetido  ");
    }

    private CreateReportDTO userReport(UUID targetId) {
        return new CreateReportDTO(ReportTargetType.USER, targetId, ReportReason.HARASSMENT, null);
    }

    @Test
    @DisplayName("Persiste denúncia de publicação como PENDING e apara o texto livre")
    void createsPostReport() {
        when(postRepository.findById(targetPost.getId())).thenReturn(Optional.of(targetPost));
        when(reportRepository.existsByReporterIdAndTargetPostId(reporter.getId(), targetPost.getId()))
                .thenReturn(false);
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(UUID.randomUUID());
            return report;
        });

        var response = reportService.createReport(reporter, postReport(targetPost.getId()));

        assertEquals(ReportTargetType.POST, response.targetType());
        assertEquals(targetPost.getId(), response.targetId());
        assertEquals(ReportStatus.PENDING, response.status());
    }

    @Test
    @DisplayName("Rejeita denúncia duplicada detectada antes do insert")
    void rejectsDuplicateDetectedByPreCheck() {
        when(postRepository.findById(targetPost.getId())).thenReturn(Optional.of(targetPost));
        when(reportRepository.existsByReporterIdAndTargetPostId(reporter.getId(), targetPost.getId()))
                .thenReturn(true);

        assertThrows(ReportAlreadyExistsException.class,
                () -> reportService.createReport(reporter, postReport(targetPost.getId())));
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("Traduz violação do índice parcial em denúncia duplicada")
    void translatesConstraintViolationIntoDuplicate() {
        when(postRepository.findById(targetPost.getId())).thenReturn(Optional.of(targetPost));
        when(reportRepository.existsByReporterIdAndTargetPostId(reporter.getId(), targetPost.getId()))
                .thenReturn(false);
        when(reportRepository.saveAndFlush(any(Report.class))).thenThrow(
                new DataIntegrityViolationException("duplicate",
                        new ConstraintViolationException("duplicate", null, "reports_unique_post_per_reporter")));

        assertThrows(ReportAlreadyExistsException.class,
                () -> reportService.createReport(reporter, postReport(targetPost.getId())));
    }

    @Test
    @DisplayName("Propaga violação de integridade não relacionada a duplicata")
    void rethrowsUnrelatedConstraintViolation() {
        when(postRepository.findById(targetPost.getId())).thenReturn(Optional.of(targetPost));
        when(reportRepository.existsByReporterIdAndTargetPostId(reporter.getId(), targetPost.getId()))
                .thenReturn(false);
        when(reportRepository.saveAndFlush(any(Report.class))).thenThrow(
                new DataIntegrityViolationException("outra",
                        new ConstraintViolationException("outra", null, "reports_reporter_id_fkey")));

        assertThrows(DataIntegrityViolationException.class,
                () -> reportService.createReport(reporter, postReport(targetPost.getId())));
    }

    @Test
    @DisplayName("Impede denúncia da própria publicação")
    void rejectsSelfPostReport() {
        targetPost.setUser(reporter);
        when(postRepository.findById(targetPost.getId())).thenReturn(Optional.of(targetPost));

        assertThrows(CannotReportSelfException.class,
                () -> reportService.createReport(reporter, postReport(targetPost.getId())));
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("Impede denúncia do próprio perfil sem consultar o repositório")
    void rejectsSelfUserReport() {
        assertThrows(CannotReportSelfException.class,
                () -> reportService.createReport(reporter, userReport(reporter.getId())));
        verify(userRepository, never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Rejeita denúncia de publicação já removida pela moderação")
    void rejectsReportOnRemovedPost() {
        targetPost.setIsRemoved(true);
        when(postRepository.findById(targetPost.getId())).thenReturn(Optional.of(targetPost));

        assertThrows(ReportTargetNotFoundException.class,
                () -> reportService.createReport(reporter, postReport(targetPost.getId())));
    }

    @Test
    @DisplayName("Rejeita denúncia de publicação inexistente")
    void rejectsMissingPost() {
        UUID unknownId = UUID.randomUUID();
        when(postRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ReportTargetNotFoundException.class,
                () -> reportService.createReport(reporter, postReport(unknownId)));
    }

    @Test
    @DisplayName("Persiste denúncia de perfil de outro usuário")
    void createsUserReport() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(reportRepository.existsByReporterIdAndTargetUserId(reporter.getId(), targetUser.getId()))
                .thenReturn(false);
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(UUID.randomUUID());
            return report;
        });

        var response = reportService.createReport(reporter, userReport(targetUser.getId()));

        assertEquals(ReportTargetType.USER, response.targetType());
        assertEquals(targetUser.getId(), response.targetId());
    }
}
