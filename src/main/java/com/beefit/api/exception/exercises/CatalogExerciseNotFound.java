package com.beefit.api.exception.exercises;

public class CatalogExerciseNotFound extends RuntimeException {
    public CatalogExerciseNotFound() {
        super("Exercício do catálogo não encontrado. ");
    }
}
