package com.beefit.api.exception.report;

public class ReportTargetNotFoundException extends RuntimeException {
    public ReportTargetNotFoundException() {
        super("Conteúdo denunciado não encontrado.");
    }
}
