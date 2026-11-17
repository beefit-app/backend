package com.beefit.api.exception.user;

/**
 * Cadastro de novas contas desligado (beefit.registration.enabled=false).
 * Estende PermissionException para manter o contrato HTTP de /auth/register
 * (403 tratado pelo handler de PermissionException) e, ao mesmo tempo, permitir
 * que o login social distinga esse caso e devolva um erro específico ao app.
 */
public class RegistrationClosedException extends PermissionException {
    public RegistrationClosedException(String message) {
        super(message);
    }
}
