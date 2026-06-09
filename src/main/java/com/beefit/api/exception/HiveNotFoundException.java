package com.beefit.api.exception;

public class HiveNotFoundException extends RuntimeException {
    public HiveNotFoundException() {
        super("Colmeia não encontrada.");
    }
}
