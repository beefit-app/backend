package com.beefit.api.exception.friendship;

public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException() {
        super("Post não encontrado.");
    }
}
