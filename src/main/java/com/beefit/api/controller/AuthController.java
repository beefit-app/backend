package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.AuthenticationDTO;
import com.beefit.api.dto.LoginResponseDTO;
import com.beefit.api.dto.RegisterDTO;
import com.beefit.api.exception.EmailAlreadyInUse;
import com.beefit.api.exception.UsernameAlreadyInUse;
import com.beefit.api.mapper.RegisterMapper;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegisterMapper registerMapper;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;

    // Endpoint de Login
    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Valid AuthenticationDTO data){

        var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.password());
        var auth = authenticationManager.authenticate(usernamePassword);

        var token = tokenService.generateToken((User) auth.getPrincipal());

        return ResponseEntity.ok(new LoginResponseDTO(token)); // Só quebrando o galho aqui, ta sempre retornando status ok com corpo vazio

    }

    // Endpoint de Cadastro
    @PostMapping("/register")
    public ResponseEntity register(@RequestBody @Valid RegisterDTO data){

        User registred = registerMapper.toEntity(data);

        // Chama o service passando os dados de cadastro
        try {
            this.authService.register(registred);
        } catch (UsernameAlreadyInUse e) {
            return ResponseEntity.badRequest().build();
        } catch (EmailAlreadyInUse e) {
            return ResponseEntity.badRequest().build();
        }

        // Retornando sucesso sempre por enquanto
        return ResponseEntity.ok().build();
    }
}
