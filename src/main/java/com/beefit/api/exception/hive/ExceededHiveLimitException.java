package com.beefit.api.exception.hive;

public class ExceededHiveLimitException extends RuntimeException {
    public ExceededHiveLimitException() {
        super("O limite de participação em colmeias foi alcançado para esse usuário.");
    }
}
