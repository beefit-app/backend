package com.beefit.api.exception;

public class EmailAlreadyInUse extends RuntimeException {
    public EmailAlreadyInUse(String message) {
        super(message);
    }
}
