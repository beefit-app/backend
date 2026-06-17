package com.beefit.api.exception.workout;

public class WorkoutAlreadyInProgressException extends RuntimeException {
    public WorkoutAlreadyInProgressException() {
        super("Usuário já possui um treino em andamento. ");
    }
}
