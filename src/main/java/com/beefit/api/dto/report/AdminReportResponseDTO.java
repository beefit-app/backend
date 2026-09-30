package com.beefit.api.dto.report;

import com.beefit.api.domain.enums.ReportReason;
import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.domain.enums.ReportTargetType;

import java.time.LocalDateTime;
import java.util.UUID;

// Visão da fila de triagem: além do alvo, identifica quem denunciou e quem é o
// dono do conteúdo, para que o moderador não precise de uma segunda consulta.
public record AdminReportResponseDTO(UUID id,
                                     UUID reporterId,
                                     String reporterUsername,
                                     ReportTargetType targetType,
                                     UUID targetId,
                                     String targetPostTitle,
                                     UUID targetOwnerId,
                                     String targetOwnerUsername,
                                     ReportReason reason,
                                     String details,
                                     ReportStatus status,
                                     LocalDateTime createdAt) {
}
