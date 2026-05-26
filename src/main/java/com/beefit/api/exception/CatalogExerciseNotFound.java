package com.beefit.api.exception;

public class CatalogExerciseNotFound extends RuntimeException {
    public CatalogExerciseNotFound() {
        super("Exercício do catálogo não encontrado. ");
    }
}
