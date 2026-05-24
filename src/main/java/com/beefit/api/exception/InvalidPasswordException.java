package com.beefit.api.exception;

public class InvalidPasswordException extends IllegalArgumentException {
    public InvalidPasswordException(String warning) {
        super(warning);
    }
}
