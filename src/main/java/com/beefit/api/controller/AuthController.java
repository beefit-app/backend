package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.auth.AuthenticationDTO;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.dto.auth.LogoutRequestDTO;
import com.beefit.api.dto.auth.RefreshTokenRequestDTO;
import com.beefit.api.dto.auth.RefreshTokenResponseDTO;
import com.beefit.api.dto.auth.RegisterDTO;
import com.beefit.api.dto.auth.UserResponseDTO;
import com.beefit.api.mapper.RegisterMapper;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import com.beefit.api.dto.auth.LegalVersionsResponseDTO;
import com.beefit.api.exception.user.PermissionException;
import com.beefit.api.service.LegalConsentService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements GenericController {

    private final AuthService authService;
    private final RegisterMapper registerMapper;
    private final UserResponseMapper userResponseMapper;
    private final RateLimitService rateLimitService;
    private final LegalConsentService legalConsentService;

    @Value("${beefit.registration.enabled:true}")
    private boolean registrationEnabled;

    // Endpoint de Login
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid AuthenticationDTO data,
            HttpServletRequest request) {

        rateLimitService.checkLogin(request.getRemoteAddr(), data.email());

        LoginResponseDTO login = authService.login(data.email(), data.password(), data.deviceId());

        return ResponseEntity.ok(login);
    }

    // Endpoint de Cadastro
    @PostMapping("/register")
    public ResponseEntity<LoginResponseDTO> register(@RequestBody @Valid RegisterDTO data,
            HttpServletRequest request) {

        if (!registrationEnabled) {
            throw new PermissionException("O cadastro de novas contas está temporariamente fechado.");
        }

        rateLimitService.checkRegistration(request.getRemoteAddr());

        User registered = registerMapper.toEntity(data);
        authService.recordLegalConsent(registered, data.acceptedTermsVersion(), data.acceptedPrivacyVersion());

        // Chama o service passando os dados de cadastro
        this.authService.register(registered);

        LoginResponseDTO login = authService.login(data.email(), data.password(), data.deviceId());
        URI location = gerarLocation(registered.getId());

        // Retornando sucesso sempre por enquanto
        return ResponseEntity.created(location).body(login);
    }

    @GetMapping("/legal-versions")
    public ResponseEntity<LegalVersionsResponseDTO> legalVersions() {
        return ResponseEntity.ok(new LegalVersionsResponseDTO(
                legalConsentService.termsVersion(), legalConsentService.privacyVersion()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponseDTO> refresh(@RequestBody @Valid RefreshTokenRequestDTO data,
            HttpServletRequest request) {
        rateLimitService.checkRefresh(request.getRemoteAddr(), data.deviceId());
        return ResponseEntity.ok(authService.refreshSession(data.refreshToken(), data.deviceId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequestDTO data) {
        authService.logout(data.refreshToken(), data.deviceId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getAuthUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userResponseMapper.toDto(user));
    }
}
