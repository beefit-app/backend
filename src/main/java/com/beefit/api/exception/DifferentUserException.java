package com.beefit.api.exception;

public class DifferentUserException extends RuntimeException{
    public DifferentUserException() {
        super("Usuário não permitido para essa ação.");
    }
}
