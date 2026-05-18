package com.beefit.api.controller;

import com.beefit.api.dto.AuthenticationDTO;
import com.beefit.api.dto.RegisterDTO;
import com.beefit.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    // Endpoint de Login
    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Valid AuthenticationDTO data){

        return ResponseEntity.ok().build(); // Só quebrando o galho aqui, ta sempre retornando status ok com corpo vazio

    }

    // Endpoint de Cadastro
    @PostMapping("/register")
    public ResponseEntity register(@RequestBody @Valid RegisterDTO data){

        // Chama o service passando os dados de cadastro
        this.authService.register(data);

        // Retornando sucesso sempre por enquanto
        return ResponseEntity.ok().build();
    }
}
