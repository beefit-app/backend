package com.beefit.api.exception.user;

public class UsernameAlreadyInUse extends RuntimeException {
    public UsernameAlreadyInUse(String message) {
        super(message);
    }
}
