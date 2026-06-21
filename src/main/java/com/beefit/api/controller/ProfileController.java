package com.beefit.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.beefit.api.domain.User;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.dto.user.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.user.UpdateProfileRequestDTO;
import com.beefit.api.dto.user.UserProfileResponseDTO;
import com.beefit.api.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private UserService userService;

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
