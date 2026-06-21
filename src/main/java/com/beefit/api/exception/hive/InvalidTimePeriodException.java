package com.beefit.api.exception.hive;

public class InvalidTimePeriodException extends RuntimeException {
    public InvalidTimePeriodException() {
        super("O período de disputa definido é inválido.");
    }
}
