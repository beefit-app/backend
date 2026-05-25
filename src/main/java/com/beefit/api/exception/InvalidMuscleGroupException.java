package com.beefit.api.exception;

public class InvalidMuscleGroupException extends RuntimeException {
    public InvalidMuscleGroupException(String invalidMuscleGroup) {
        super("Grupo muscular inválido: " + invalidMuscleGroup);
    }
}
