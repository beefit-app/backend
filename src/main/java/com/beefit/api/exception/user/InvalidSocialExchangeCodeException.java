package com.beefit.api.exception.user;

public class InvalidSocialExchangeCodeException extends RuntimeException {

    public InvalidSocialExchangeCodeException(String message) {
        super(message);
    }
}
