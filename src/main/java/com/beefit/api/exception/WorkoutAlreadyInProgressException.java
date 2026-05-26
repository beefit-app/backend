package com.beefit.api.exception;

public class WorkoutAlreadyInProgressException extends RuntimeException {
    public WorkoutAlreadyInProgressException() {
        super("Usuário já possui um treino em andamento. ");
    }
}
