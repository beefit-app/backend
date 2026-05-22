package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.ChangePasswordDTO;
import com.beefit.api.dto.DeleteUserRequest;
import com.beefit.api.dto.UpdateProfileRequest;
import com.beefit.api.dto.UserProfileResponse;
import com.beefit.api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private UserService userService;

    // Rota para Visualizar o Perfil: GET http://localhost:8080/api/profile/{id}
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        UserProfileResponse response = userService.getUserProfile(user.getId());
        return ResponseEntity.ok(response); // Retorna HTTP 200 OK com os dados do perfil
    }

    // Rota para Editar o Perfil: PUT http://localhost:8080/api/profile/{id}
    @PutMapping("/me")
    public ResponseEntity<Void> updateProfile(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateUserProfile(user.getId(), request);
        return ResponseEntity.noContent().build(); // Retorna HTTP 204 No Content (sucesso sem corpo de resposta)
    }

    @PutMapping("/me/password")
    // Se a senha for inválida, o Spring retorna um erro 400 (Bad Request) automaticamente
    public ResponseEntity<String> changePassword(@AuthenticationPrincipal User user, @Valid @RequestBody ChangePasswordDTO dto) {
        userService.changeUserPassword(user.getId(), dto);
        return ResponseEntity.ok("Senha alterada com sucesso!");
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User user, @Valid @RequestBody DeleteUserRequest dto) {
        userService.deleteAccount(user.getId(), dto);
        return ResponseEntity.noContent().build();
    }
}