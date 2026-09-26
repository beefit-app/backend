package com.beefit.api.dto.report;

import com.beefit.api.domain.enums.ReportResolutionAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ResolveReportDTO(@NotNull(message = "Informe a decisão sobre a denúncia.")
                               ReportResolutionAction action,
                               @Min(value = 1, message = "O banimento deve ter pelo menos 1 dia.")
                               @Max(value = 365, message = "O banimento deve ter no máximo 365 dias.")
                               Integer banDays) {
}
