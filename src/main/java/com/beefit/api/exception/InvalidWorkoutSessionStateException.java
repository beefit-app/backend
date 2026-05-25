package com.beefit.api.exception;

public class InvalidWorkoutSessionStateException extends RuntimeException {
    public InvalidWorkoutSessionStateException() {
        super("Estado de sessão de treino inválido. ");
    }
}
