package com.beefit.api.exception.workout;

public class InvalidMuscleGroupException extends RuntimeException {
    public InvalidMuscleGroupException(String invalidMuscleGroup) {
        super("Grupo muscular inválido: " + invalidMuscleGroup);
    }
}
