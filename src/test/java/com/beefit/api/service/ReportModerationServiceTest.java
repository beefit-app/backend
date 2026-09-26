package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.Report;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.ReportReason;
import com.beefit.api.domain.enums.ReportResolutionAction;
import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.domain.enums.ReportTargetType;
import com.beefit.api.dto.report.AdminReportResponseDTO;
import com.beefit.api.dto.report.ResolveReportDTO;
import com.beefit.api.dto.report.ResolvedReportResponseDTO;
import com.beefit.api.exception.report.ReportAlreadyResolvedException;
import com.beefit.api.exception.report.ReportNotFoundException;
import com.beefit.api.repository.ReportRepository;

@ExtendWith(MockitoExtension.class)
class ReportModerationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private WorkoutModerationService workoutModerationService;

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private ReportModerationService reportModerationService;

    private final UUID adminId = UUID.randomUUID();
    private User reporter;
    private User owner;
    private Post post;

    @BeforeEach
    void setUp() {
        reporter = new User();
        reporter.setId(UUID.randomUUID());
        reporter.setUsername("reporter");

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("owner");

        post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(owner);
        post.setTitle("Treino de peito");
    }

    private Report postReport(ReportStatus status) {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporter(reporter);
        report.setTargetType(ReportTargetType.POST);
        report.setTargetPost(post);
        report.setReason(ReportReason.SPAM);
        report.setDetails("repetido");
        report.setStatus(status);
        return report;
    }

    private Report userReport() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporter(reporter);
        report.setTargetType(ReportTargetType.USER);
        report.setTargetUser(owner);
        report.setReason(ReportReason.HARASSMENT);
        report.setStatus(ReportStatus.PENDING);
        return report;
    }

    @Test
    @DisplayName("Remover post aplica a moderação e fecha as pendentes do post como analisadas")
    void removePostClosesPendingReportsOfThePost() {
        Report report = postReport(ReportStatus.PENDING);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(reportRepository.closePendingByTargetPost(post.getId(), ReportStatus.REVIEWED)).thenReturn(3);

        ResolvedReportResponseDTO response = reportModerationService.resolveReport(adminId, report.getId(),
                new ResolveReportDTO(ReportResolutionAction.REMOVE_POST, null));

        verify(workoutModerationService).removeInappropriatePost(post.getId());
        verifyNoInteractions(adminUserService);
        assertEquals(ReportStatus.REVIEWED, response.status());
        assertEquals(3, response.closedReports());
    }

    @Test
    @DisplayName("Banir em denúncia de post bane o dono do post, não quem denunciou")
    void banOnPostReportBansThePostOwner() {
        Report report = postReport(ReportStatus.PENDING);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(reportRepository.closePendingByTargetPost(post.getId(), ReportStatus.REVIEWED)).thenReturn(1);

        reportModerationService.resolveReport(adminId, report.getId(),
                new ResolveReportDTO(ReportResolutionAction.BAN_USER, 14));

        verify(adminUserService).banUserForDays(adminId, owner.getId(), 14);
        verify(workoutModerationService, never()).removeInappropriatePost(any());
    }

    @Test
    @DisplayName("Banir em denúncia de perfil bane o perfil e fecha as pendentes do perfil")
    void banOnUserReportClosesPendingReportsOfTheUser() {
        Report report = userReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(reportRepository.closePendingByTargetUser(owner.getId(), ReportStatus.REVIEWED)).thenReturn(2);

        ResolvedReportResponseDTO response = reportModerationService.resolveReport(adminId, report.getId(),
                new ResolveReportDTO(ReportResolutionAction.BAN_USER, 7));

        verify(adminUserService).banUserForDays(adminId, owner.getId(), 7);
        assertEquals(2, response.closedReports());
    }

    @Test
    @DisplayName("Descartar não aplica punição e fecha as pendentes como descartadas")
    void dismissOnlyClosesReports() {
        Report report = postReport(ReportStatus.PENDING);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(reportRepository.closePendingByTargetPost(post.getId(), ReportStatus.DISMISSED)).thenReturn(1);

        ResolvedReportResponseDTO response = reportModerationService.resolveReport(adminId, report.getId(),
                new ResolveReportDTO(ReportResolutionAction.DISMISS, null));

        verifyNoInteractions(workoutModerationService, adminUserService);
        assertEquals(ReportStatus.DISMISSED, response.status());
    }

    @Test
    @DisplayName("Denúncia inexistente devolve não encontrada")
    void missingReportThrowsNotFound() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findById(reportId)).thenReturn(Optional.empty());

        assertThrows(ReportNotFoundException.class, () -> reportModerationService.resolveReport(adminId, reportId,
                new ResolveReportDTO(ReportResolutionAction.DISMISS, null)));
    }

    @Test
    @DisplayName("Denúncia já decidida não pode ser decidida de novo")
    void alreadyResolvedReportIsRejected() {
        Report report = postReport(ReportStatus.REVIEWED);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        assertThrows(ReportAlreadyResolvedException.class, () -> reportModerationService.resolveReport(adminId,
                report.getId(), new ResolveReportDTO(ReportResolutionAction.REMOVE_POST, null)));
        verifyNoInteractions(workoutModerationService, adminUserService);
    }

    @Test
    @DisplayName("Remover post em denúncia de perfil é rejeitado sem punir ninguém")
    void removePostOnUserReportIsRejected() {
        Report report = userReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        assertThrows(IllegalArgumentException.class, () -> reportModerationService.resolveReport(adminId,
                report.getId(), new ResolveReportDTO(ReportResolutionAction.REMOVE_POST, null)));
        verifyNoInteractions(workoutModerationService, adminUserService);
        verify(reportRepository, never()).closePendingByTargetUser(any(), any());
    }

    @Test
    @DisplayName("A fila mostra o título do post denunciado e o dono como alvo")
    void listIncludesPostTitleAndOwner() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Report> page = new PageImpl<>(List.of(postReport(ReportStatus.PENDING), userReport()), pageable, 2);
        when(reportRepository.findAllByStatus(ReportStatus.PENDING, pageable)).thenReturn(page);

        List<AdminReportResponseDTO> content = reportModerationService
                .listReports(ReportStatus.PENDING, pageable).getContent();

        assertEquals("Treino de peito", content.get(0).targetPostTitle());
        assertEquals("owner", content.get(0).targetOwnerUsername());
        assertNull(content.get(1).targetPostTitle());
        assertEquals(owner.getId(), content.get(1).targetId());
    }
}
