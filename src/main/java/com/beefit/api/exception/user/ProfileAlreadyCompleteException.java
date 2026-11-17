package com.beefit.api.exception.user;

public class ProfileAlreadyCompleteException extends RuntimeException {

    public ProfileAlreadyCompleteException() {
        super("O cadastro desta conta já está completo.");
    }
}
