package com.beefit.api.exception;

public class DatabaseSeederFailException extends RuntimeException {
    public DatabaseSeederFailException(String message, Throwable cause) {
        super(message, cause);
    }
}
