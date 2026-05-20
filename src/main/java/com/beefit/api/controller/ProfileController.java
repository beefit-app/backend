package com.beefit.api.controller;

import com.beefit.api.dto.DeleteUserRequest;
import com.beefit.api.dto.UpdateProfileRequest;
import com.beefit.api.dto.UserProfileResponse;
import com.beefit.api.dto.ChangePasswordDTO;
import com.beefit.api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private UserService userService;

    // Rota para Visualizar o Perfil: GET http://localhost:8080/api/profile/{id}
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID id) {
        UserProfileResponse response = userService.getUserProfile(id);
        return ResponseEntity.ok(response); // Retorna HTTP 200 OK com os dados do perfil
    }

    // Rota para Editar o Perfil: PUT http://localhost:8080/api/profile/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateProfile(@PathVariable UUID id, @RequestBody UpdateProfileRequest request) {
        userService.updateUserProfile(id, request);
        return ResponseEntity.noContent().build(); // Retorna HTTP 204 No Content (sucesso sem corpo de resposta)
    }

    @PutMapping("/{id}/senha")
    // Se a senha for inválida, o Spring retorna um erro 400 (Bad Request) automaticamente
    public ResponseEntity<String> changePassword(@PathVariable UUID id, @Valid @RequestBody ChangePasswordDTO dto) {
        userService.changeUserPassword(id, dto);
        return ResponseEntity.ok("Senha alterada com sucesso!");
    }

    @DeleteMapping("/{id}/me")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id, @Valid @RequestBody DeleteUserRequest dto) {
        userService.deleteAccount(id, dto);
        return ResponseEntity.noContent().build();
    }
}