package com.beefit.api.dto.report;

import com.beefit.api.domain.enums.ReportResolutionAction;
import com.beefit.api.domain.enums.ReportStatus;

import java.util.UUID;

// closedReports conta a denúncia decidida e as demais pendentes do mesmo alvo, que
// são fechadas junto para a moderação não julgar o mesmo conteúdo várias vezes.
public record ResolvedReportResponseDTO(UUID reportId,
                                        ReportResolutionAction action,
                                        ReportStatus status,
                                        int closedReports) {
}
