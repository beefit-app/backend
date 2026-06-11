package com.beefit.api.exception;

public class PermissionException extends RuntimeException {
    public PermissionException() {
        super("O usuário não tem permissão para essa ação.");
    }
}
