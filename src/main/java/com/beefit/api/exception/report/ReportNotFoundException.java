package com.beefit.api.exception.report;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException() {
        super("Denúncia não encontrada.");
    }
}
