package com.beefit.api.exception.user;

public class PermissionException extends RuntimeException {
    public PermissionException() {
        super("O usuário não tem permissão para essa ação.");
    }
}
