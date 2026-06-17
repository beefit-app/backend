package com.beefit.api.exception.exercises;

public class DatabaseSeederFailException extends RuntimeException {
    public DatabaseSeederFailException(String message, Throwable cause) {
        super(message, cause);
    }
}
