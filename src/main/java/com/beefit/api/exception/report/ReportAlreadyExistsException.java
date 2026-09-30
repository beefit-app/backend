package com.beefit.api.exception.report;

public class ReportAlreadyExistsException extends RuntimeException {
    public ReportAlreadyExistsException() {
        super("Você já denunciou este conteúdo. Nossa equipe está analisando.");
    }
}
