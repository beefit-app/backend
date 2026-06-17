package com.beefit.api.exception.user;

public class DifferentUserException extends RuntimeException{
    public DifferentUserException() {
        super("Usuário não permitido para essa ação.");
    }
}
