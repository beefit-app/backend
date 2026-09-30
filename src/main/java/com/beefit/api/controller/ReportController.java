package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.report.CreateReportDTO;
import com.beefit.api.dto.report.ReportResponseDTO;
import com.beefit.api.service.RateLimitService;
import com.beefit.api.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Registro de denúncias de conteúdo gerado por usuário, exigido pelas diretrizes
// de UGC da Apple e do Google.
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final RateLimitService rateLimitService;

    @PostMapping
    public ResponseEntity<ReportResponseDTO> create(@AuthenticationPrincipal User user,
            @Valid @RequestBody CreateReportDTO dto) {
        rateLimitService.checkReport(user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(user, dto));
    }
}
