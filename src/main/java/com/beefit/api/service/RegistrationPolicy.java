package com.beefit.api.service;

import com.beefit.api.exception.user.RegistrationClosedException;
import com.beefit.api.exception.user.RegistrationRestrictedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Regras de quem pode criar conta, compartilhadas pelo cadastro com e-mail/senha
 * (AuthController/AuthService) e pelo login social (SocialAuthService). Ficam num
 * lugar só para que nenhuma porta de entrada crie conta ignorando o cadastro
 * fechado ou a lista de convidados do beta.
 */
@Component
public class RegistrationPolicy {

    @Value("${beefit.registration.enabled:true}")
    private boolean registrationEnabled;

    @Value("${beefit.beta.enabled:false}")
    private boolean betaEnabled;

    @Value("${beefit.beta.allowed-emails:}")
    private String[] allowedEmails;

    public void ensureRegistrationOpen() {
        if (!registrationEnabled) {
            throw new RegistrationClosedException("O cadastro de novas contas está temporariamente fechado.");
        }
    }

    public void ensureEmailAllowed(String email) {
        if (!betaEnabled) {
            return;
        }
        String candidate = email == null ? "" : email.trim();
        if (allowedEmails != null) {
            for (String allowed : allowedEmails) {
                if (allowed != null && !allowed.isBlank() && allowed.trim().equalsIgnoreCase(candidate)) {
                    return;
                }
            }
        }
        throw new RegistrationRestrictedException(
                "O cadastro está restrito a participantes autorizados durante o beta.");
    }
}
