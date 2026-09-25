package com.beefit.api.controller;

import com.beefit.api.domain.enums.ReportStatus;
import com.beefit.api.dto.report.AdminReportResponseDTO;
import com.beefit.api.service.ReportModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Fila de triagem das denúncias. Rotas protegidas (acesso exclusivo para administradores).
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportModerationService reportModerationService;

    @GetMapping
    public ResponseEntity<Page<AdminReportResponseDTO>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reportModerationService.listReports(status, pageable));
    }
}
