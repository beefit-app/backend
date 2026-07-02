package com.beefit.api.controller;

import com.beefit.api.dto.AdminDashboardMetricsDTO;
import com.beefit.api.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')") // Garante que o Token JWT tenha o perfil (Role) de administrador
    public ResponseEntity<AdminDashboardMetricsDTO> getDashboardMetrics() {

        AdminDashboardMetricsDTO metrics = adminDashboardService.getConsolidatedMetrics();

        return ResponseEntity.ok(metrics);
    }
}
