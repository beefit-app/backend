package com.beefit.api.exception.report;

public class ReportAlreadyResolvedException extends RuntimeException {
    public ReportAlreadyResolvedException() {
        super("Essa denúncia já foi resolvida.");
    }
}
