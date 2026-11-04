package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.AuthSession;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.dto.auth.RefreshTokenResponseDTO;
import com.beefit.api.dto.auth.UserResponseDTO;
import com.beefit.api.exception.user.EmailAlreadyInUse;
import com.beefit.api.exception.user.InvalidSessionException;
import com.beefit.api.exception.user.SocialLoginRequiredException;
import com.beefit.api.exception.user.UsernameAlreadyInUse;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private static final int REFRESH_TOKEN_DURATION_DAYS = 30;

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final InventoryRepository inventoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserResponseMapper userResponseMapper;
    private final TokenService tokenService;
    private final LegalConsentService legalConsentService;
    private final AuthenticationConfiguration authenticationConfiguration;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Value("${beefit.beta.enabled:false}")
    private boolean betaEnabled;

    @org.springframework.beans.factory.annotation.Value("${beefit.beta.allowed-emails:}")
    private String[] allowedEmails;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserDetails user = userRepository.findByEmailIgnoreCase(normalizeEmail(email));

        if(user == null){
            throw new UsernameNotFoundException("Usuário não encontrado");
        }

        return user;
    }

    @Transactional
    public LoginResponseDTO login(String email, String password, String deviceId) {
        String normalizedEmail = normalizeEmail(email);

        // BCryptPasswordEncoder.matches lanca IllegalArgumentException para hash nulo,
        // o que o DaoAuthenticationProvider interno do authenticationManager nao trata
        // como falha de credenciais. Contas criadas via login social nao tem senha
        // local (passwordHash == null), entao essa checagem precisa vir antes dele.
        userRepository.findOptionalByEmailIgnoreCase(normalizedEmail)
                .filter(user -> user.getPasswordHash() == null)
                .ifPresent(user -> {
                    throw new SocialLoginRequiredException(
                            "Esta conta usa login social. Entre com Google ou Apple.");
                });

        var usernamePassword = new UsernamePasswordAuthenticationToken(normalizedEmail, password);

        var authenticationManager = authenticationConfiguration.getAuthenticationManager();
        var auth = authenticationManager.authenticate(usernamePassword);
        User authenticatedUser = (User) auth.getPrincipal();
        return createSession(authenticatedUser, deviceId);
    }

    /** Emite sessão (JWT + refresh token) para um usuário já resolvido pelo fluxo de login social. */
    @Transactional
    public LoginResponseDTO loginSocialUser(User user, String deviceId) {
        return createSession(user, deviceId);
    }

    @Transactional
    public void register(User registered) throws UsernameAlreadyInUse, EmailAlreadyInUse {
        registered.setEmail(normalizeEmail(registered.getEmail()));
        registered.setUsername(normalizeUsername(registered.getUsername()));

        if (betaEnabled) {
            String email = registered.getEmail() == null ? "" : registered.getEmail();
            boolean isAllowed = false;
            if (allowedEmails != null) {
                for (String allowed : allowedEmails) {
                    if (allowed != null && !allowed.isBlank() && allowed.trim().equalsIgnoreCase(email)) {
                        isAllowed = true;
                        break;
                    }
                }
            }
            if (!isAllowed) {
                throw new com.beefit.api.exception.user.RegistrationRestrictedException(
                        "O cadastro está restrito a participantes autorizados durante o beta.");
            }
        }

        if(userRepository.findByUsernameIgnoreCase(registered.getUsername()).isPresent()){
            throw new UsernameAlreadyInUse("Esse nome de usuário já está em uso.");
        };

        if(userRepository.findByEmailIgnoreCase(registered.getEmail()) != null){
            throw new EmailAlreadyInUse("Esse email já está em uso.");
        };

        registered.setPasswordHash(passwordEncoder.encode(registered.getPasswordHash()));
        registered.setRole(UserRole.USER);

        User savedUser = userRepository.save(registered);

        Inventory inventory = new Inventory();
        inventory.setUser(savedUser);
        inventory.setTotalCards(0);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public RefreshTokenResponseDTO refreshSession(String refreshToken, String deviceId) {
        AuthSession currentSession = authSessionRepository
                .findByRefreshTokenHashAndDeviceIdAndRevokedAtIsNull(hashRefreshToken(refreshToken), deviceId)
                .orElseThrow(() -> new InvalidSessionException("Sessão inválida."));

        if (currentSession.getExpiresAt().isBefore(LocalDateTime.now())) {
            currentSession.setRevokedAt(LocalDateTime.now());
            throw new InvalidSessionException("Sessão expirada. Faça login novamente.");
        }

        currentSession.setRevokedAt(LocalDateTime.now());
        currentSession.setLastUsedAt(LocalDateTime.now());

        SessionTokens sessionTokens = createAuthSession(currentSession.getUser(), deviceId);
        return new RefreshTokenResponseDTO(sessionTokens.accessToken(), sessionTokens.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken, String deviceId) {
        authSessionRepository.findByRefreshTokenHashAndDeviceIdAndRevokedAtIsNull(hashRefreshToken(refreshToken), deviceId)
                .ifPresent(session -> {
                    session.setRevokedAt(LocalDateTime.now());
                    session.setLastUsedAt(LocalDateTime.now());
                });
    }

    private LoginResponseDTO createSession(User user, String deviceId) {
        revokeExistingSessions(user.getId(), deviceId);
        SessionTokens sessionTokens = createAuthSession(user, deviceId);
        UserResponseDTO login = userResponseMapper.toDto(user);
        return new LoginResponseDTO(sessionTokens.accessToken(), sessionTokens.refreshToken(), login);
    }

    private void revokeExistingSessions(java.util.UUID userId, String deviceId) {
        List<AuthSession> activeSessions = authSessionRepository
                .findAllByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(userId, deviceId, LocalDateTime.now());
        LocalDateTime revokedAt = LocalDateTime.now();
        for (AuthSession session : activeSessions) {
            session.setRevokedAt(revokedAt);
            session.setLastUsedAt(revokedAt);
        }
    }

    private SessionTokens createAuthSession(User user, String deviceId) {
        String refreshToken = generateRefreshToken();
        LocalDateTime now = LocalDateTime.now();

        AuthSession authSession = new AuthSession();
        authSession.setUser(user);
        authSession.setDeviceId(deviceId);
        authSession.setRefreshTokenHash(hashRefreshToken(refreshToken));
        authSession.setCreatedAt(now);
        authSession.setLastUsedAt(now);
        authSession.setExpiresAt(now.plusDays(REFRESH_TOKEN_DURATION_DAYS));

        // A sessao e gravada antes de emitir o access token porque o token carrega
        // o id dela: e esse vinculo que permite ao SecurityFilter recusar um token
        // cuja sessao ja foi revogada por logout ou troca de senha.
        AuthSession savedSession = authSessionRepository.save(authSession);
        String accessToken = tokenService.generateToken(user, savedSession.getId());

        return new SessionTokens(accessToken, refreshToken);
    }

    private String generateRefreshToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = messageDigest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível para hash de refresh token.", e);
        }
    }

    public void recordLegalConsent(User user, String termsVersion, String privacyVersion) {
        legalConsentService.recordConsent(user, termsVersion, privacyVersion);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private record SessionTokens(String accessToken, String refreshToken) {
    }

}
