package com.beefit.api.exception;

public class WorkoutTemplateNotFoundException extends RuntimeException {
    public WorkoutTemplateNotFoundException() {
        super("Template de trieno não foi encontrado. ");
    }
}
