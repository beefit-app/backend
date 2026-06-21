package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.auth.UserResponseDTO;
import com.beefit.api.dto.user.BannedUserResponseDTO;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;


@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@EnableMethodSecurity
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserResponseMapper userResponseMapper;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponseDTO>> searchUsers(
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(page = 0, size = 15, sort = "name") Pageable pageable
        ) {
        Page<User> users = adminUserService.searchUsers(search, pageable);

        Page<UserResponseDTO> response = users.map(userResponseMapper::toDto);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> showUser(@PathVariable UUID userId){

        User user = adminUserService.getUser(userId);

        UserResponseDTO response = userResponseMapper.toDto(user);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/ban")
    public ResponseEntity<BannedUserResponseDTO> banUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer banDays
            ){

        BannedUserResponseDTO response = adminUserService.banUserForDays(userId, banDays);

        return ResponseEntity.ok(response);

    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/unban")
    public ResponseEntity<BannedUserResponseDTO> unbanUser(@PathVariable UUID userId){

        BannedUserResponseDTO response = adminUserService.unbanUser(userId);

        return ResponseEntity.ok(response);

    }

}
