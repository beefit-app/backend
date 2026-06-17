package com.beefit.api.exception.workout;

public class InvalidWorkoutSessionStateException extends RuntimeException {
    public InvalidWorkoutSessionStateException(String message) {
        super(message);
    }
}
