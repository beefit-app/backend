package com.beefit.api.exception.user;

public class UnverifiedSocialEmailException extends RuntimeException {

    public UnverifiedSocialEmailException(String message) {
        super(message);
    }
}
