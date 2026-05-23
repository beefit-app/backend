package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.AuthenticationDTO;
import com.beefit.api.dto.LoginResponseDTO;
import com.beefit.api.dto.RegisterDTO;
import com.beefit.api.dto.UserResponseDTO;
import com.beefit.api.mapper.RegisterMapper;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements GenericController {

    private final AuthService authService;
    private final RegisterMapper registerMapper;
    private final UserResponseMapper userResponseMapper;

  // Endpoint de Login
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid AuthenticationDTO data) {

        LoginResponseDTO login = authService.login(data.email(), data.password());

        return ResponseEntity.ok(login);
    }

  // Endpoint de Cadastro
    @PostMapping("/register")
    public ResponseEntity<LoginResponseDTO> register(@RequestBody @Valid RegisterDTO data) {

        User registered = registerMapper.toEntity(data);

        // Chama o service passando os dados de cadastro
        this.authService.register(registered);

        LoginResponseDTO login = authService.login(data.email(), data.password());
        URI location = gerarLocation(registered.getId());

        // Retornando sucesso sempre por enquanto
        return ResponseEntity.created(location).body(login);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getAuthUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userResponseMapper.toDto(user));
    }
}
