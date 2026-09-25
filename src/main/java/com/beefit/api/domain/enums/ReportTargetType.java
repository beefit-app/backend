package com.beefit.api.domain.enums;

// Tipo de conteúdo denunciado. O alvo correspondente é a única coluna preenchida
// em reports; o CHECK reports_target_shape_check garante essa exclusividade.
public enum ReportTargetType {
    POST,
    USER
}
