package com.beefit.api.exception.user;

public class SocialLoginRequiredException extends RuntimeException {

    public SocialLoginRequiredException(String message) {
        super(message);
    }
}
