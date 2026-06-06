package com.beefit.api.exception;

public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException() {
        super("Post não encontrado.");
    }
}
