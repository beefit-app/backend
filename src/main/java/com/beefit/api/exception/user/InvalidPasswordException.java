package com.beefit.api.exception.user;

public class InvalidPasswordException extends IllegalArgumentException {
    public InvalidPasswordException(String warning) {
        super(warning);
    }
}
