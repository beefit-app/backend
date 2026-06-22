package com.beefit.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.beefit.api.domain.User;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.dto.user.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.user.UpdateProfileRequestDTO;
import com.beefit.api.dto.user.UserProfileResponseDTO;
import com.beefit.api.service.UserService;
import com.beefit.api.service.PostService;
import com.beefit.api.dto.feed.FeedPostResponseDTO;
import com.beefit.api.domain.enums.WorkoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private UserService userService;

    @Autowired
    private PostService postService;

    @GetMapping("/{username}")
    public ResponseEntity<UserProfileResponseDTO> getOtherProfile(@PathVariable String username) {
        UserProfileResponseDTO response = userService.getUserProfileByUsername(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/posts")
    public ResponseEntity<Page<FeedPostResponseDTO>> getOwnPosts(@AuthenticationPrincipal User user,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(postService.getOwnPosts(user, pageable));
    }

    @GetMapping("/me/training-days")
    public ResponseEntity<List<Integer>> getTrainingDays(@AuthenticationPrincipal User user,
            @RequestParam String month) {
        YearMonth target = YearMonth.parse(month);
        List<Integer> days = userService.getTrainingDays(user.getId(), target).stream().map(LocalDate::getDayOfMonth).toList();
        return ResponseEntity.ok(days);
    }

    // Rota para Visualizar o Perfil: GET http://localhost:8080/api/profile/{id}
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDTO> getProfile(@AuthenticationPrincipal User user) {
        UserProfileResponseDTO response = userService.getUserProfile(user.getId());
        return ResponseEntity.ok(response); // Retorna HTTP 200 OK com os dados do perfil
    }

    // Rota para Editar o Perfil: PUT http://localhost:8080/api/profile/{id}
    @PutMapping("/me")
    public ResponseEntity<Void> updateProfile(@AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequestDTO request) {
        userService.updateUserProfile(user.getId(), request);
        return ResponseEntity.noContent().build(); // Retorna HTTP 204 No Content (sucesso sem corpo de resposta)
    }

    @PutMapping("/me/password")
    // Se a senha for inválida, o Spring retorna um erro 400 (Bad Request)
    // automaticamente
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordDTO dto) {
        userService.changeUserPassword(user.getId(), dto);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/settings")
    public ResponseEntity<Void> updateAccountSettings(@AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateAccountSettingsRequestDTO request) {
        userService.updateUserGhostMode(user.getId(), request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User user,
            @Valid @RequestBody DeleteUserRequestDTO dto) {
        userService.deleteAccount(user.getId(), dto);
        return ResponseEntity.noContent().build();
    }
}
