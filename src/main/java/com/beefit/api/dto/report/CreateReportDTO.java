package com.beefit.api.dto.report;

import com.beefit.api.domain.enums.ReportReason;
import com.beefit.api.domain.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// As mensagens vao para ErrorResponse.fields e sao exibidas embaixo do campo
// correspondente no formulario de denuncia, entao sao escritas para o usuario final.
public record CreateReportDTO(@NotNull(message = "Informe o que está sendo denunciado.")
                              ReportTargetType targetType,

                              @NotNull(message = "Informe o que está sendo denunciado.")
                              UUID targetId,

                              @NotNull(message = "Selecione um motivo para a denúncia.")
                              ReportReason reason,

                              @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
                              String details) {
}
