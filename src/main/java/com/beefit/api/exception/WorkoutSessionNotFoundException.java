package com.beefit.api.exception;

public class WorkoutSessionNotFoundException extends RuntimeException {
    public WorkoutSessionNotFoundException() {
        super("Sessão de treino não encontrada. ");
    }
}
