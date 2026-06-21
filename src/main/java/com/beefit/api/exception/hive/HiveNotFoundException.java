package com.beefit.api.exception.hive;

public class HiveNotFoundException extends RuntimeException {
    public HiveNotFoundException() {
        super("Colmeia não encontrada.");
    }
}
