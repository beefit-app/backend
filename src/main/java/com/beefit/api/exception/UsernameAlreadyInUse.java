package com.beefit.api.exception;

public class UsernameAlreadyInUse extends RuntimeException {
    public UsernameAlreadyInUse(String message) {
        super(message);
    }
}
