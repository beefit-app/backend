package com.beefit.api.exception.report;

public class CannotReportSelfException extends RuntimeException {
    public CannotReportSelfException() {
        super("Não é possível denunciar o próprio conteúdo.");
    }
}
