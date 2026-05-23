package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.AuthenticationDTO;
import com.beefit.api.dto.LoginResponseDTO;
import com.beefit.api.dto.RegisterDTO;
import com.beefit.api.dto.UserResponseDTO;
import com.beefit.api.exception.EmailAlreadyInUse;
import com.beefit.api.exception.UsernameAlreadyInUse;
import com.beefit.api.mapper.RegisterMapper;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.bind.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final RegisterMapper registerMapper;
  private final UserResponseMapper userResponseMapper;
  private final TokenService tokenService;

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

    // Retornando sucesso sempre por enquanto
    return ResponseEntity.ok(login);
  }

  @GetMapping("/me")
  public UserResponseDTO getAuthUser(@AuthenticationPrincipal User user) {
    return userResponseMapper.toDto(user);
  }
}
