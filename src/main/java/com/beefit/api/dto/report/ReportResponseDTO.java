package com.beefit.api.dto.report;

import com.beefit.api.domain.enums.ReportReason;
import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.domain.enums.ReportTargetType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReportResponseDTO(UUID id,
                                ReportTargetType targetType,
                                UUID targetId,
                                ReportReason reason,
                                ReportStatus status,
                                LocalDateTime createdAt) {
}
