package com.beefit.api.exception.workout;

public class WorkoutTemplateNotFoundException extends RuntimeException {
    public WorkoutTemplateNotFoundException() {
        super("Template de trieno não foi encontrado. ");
    }
}
