package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.AdminUserResponseDTO;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserResponseMapper userResponseMapper;

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminUserResponseDTO>> searchUsers(
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(page = 0, size = 15, sort = "fullName") Pageable pageable
        ) {
        Page<User> users = adminUserService.searchUsers(search, pageable);

        Page<AdminUserResponseDTO> response = users.map(this::toAdminDto);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> showUser(@PathVariable("id") UUID userId){

        User user = adminUserService.getUser(userId);

        UserResponseDTO response = userResponseMapper.toDto(user);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/ban")
    public ResponseEntity<BannedUserResponseDTO> banUser(
            @AuthenticationPrincipal User admin,
            @PathVariable("id") UUID userId,
            @RequestParam(required = false) Integer banDays
            ){

        BannedUserResponseDTO response = adminUserService.banUserForDays(admin.getId(), userId, banDays);

        return ResponseEntity.ok(response);

    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/unban")
    public ResponseEntity<BannedUserResponseDTO> unbanUser(@PathVariable("id") UUID userId){

        BannedUserResponseDTO response = adminUserService.unbanUser(userId);

        return ResponseEntity.ok(response);

    }

    private AdminUserResponseDTO toAdminDto(User user) {
        return new AdminUserResponseDTO(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getBodyWeight(),
                user.getWeeklyGoal(),
                user.isBanned(),
                user.getBannedUntil()
        );
    }

}
