package com.beefit.api.exception.user;

public class RegistrationRestrictedException extends RuntimeException {
    public RegistrationRestrictedException(String message) {
        super(message);
    }
}
