package com.beefit.api.exception;

public class InvalidWorkoutSessionStateException extends RuntimeException {
    public InvalidWorkoutSessionStateException(String message) {
        super(message);
    }
}
