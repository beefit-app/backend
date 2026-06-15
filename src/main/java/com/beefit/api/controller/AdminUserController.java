package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.awt.print.Pageable;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@EnableMethodSecurity
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<User>> searchUsers(
            @AuthenticationPrincipal User admin,
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 14, sort = "name") Pageable pageable){

        Page<User> users = adminUserService.searchUsers(search, pageable);
        return ResponseEntity.ok(users);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> showUser(){

    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> toggleAccountStatus(){

    }

}
