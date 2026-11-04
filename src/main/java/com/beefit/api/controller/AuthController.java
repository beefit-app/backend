package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.auth.AuthenticationDTO;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.dto.auth.CompleteProfileRequestDTO;
import com.beefit.api.dto.auth.LogoutRequestDTO;
import com.beefit.api.dto.auth.RefreshTokenRequestDTO;
import com.beefit.api.dto.auth.RefreshTokenResponseDTO;
import com.beefit.api.dto.auth.RegisterDTO;
import com.beefit.api.dto.auth.SocialExchangeRequestDTO;
import com.beefit.api.dto.auth.UserResponseDTO;
import com.beefit.api.exception.user.InvalidSocialExchangeCodeException;
import com.beefit.api.infra.security.oauth2.SocialExchangeCodeStore;
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
import com.beefit.api.service.RegistrationPolicy;
import com.beefit.api.dto.auth.LegalVersionsResponseDTO;
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
    private final SocialExchangeCodeStore socialExchangeCodeStore;

    private final RegistrationPolicy registrationPolicy;

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

        registrationPolicy.ensureRegistrationOpen();

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

    // Troca o código de uso único emitido pelo callback de login social (Google/Microsoft)
    // pela sessão real (JWT + refresh token). Ver OAuth2LoginSuccessHandler/SocialExchangeCodeStore.
    @PostMapping("/social/exchange")
    public ResponseEntity<LoginResponseDTO> exchangeSocialCode(@RequestBody @Valid SocialExchangeRequestDTO data) {
        LoginResponseDTO session = socialExchangeCodeStore.consume(data.code())
                .orElseThrow(() -> new InvalidSocialExchangeCodeException(
                        "Código de login social inválido ou expirado. Tente novamente."));
        return ResponseEntity.ok(session);
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

    // Exclusivo de conta criada via login social que ainda não informou peso, meta
    // e aceite dos termos (ver User.isProfileComplete / ProfileCompletionFilter).
    @PostMapping("/complete-profile")
    public ResponseEntity<UserResponseDTO> completeProfile(@AuthenticationPrincipal User user,
            @RequestBody @Valid CompleteProfileRequestDTO data) {
        return ResponseEntity.ok(authService.completeProfile(user, data));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getAuthUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userResponseMapper.toDto(user));
    }
}
