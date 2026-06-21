package com.beefit.api.exception;

public class InvalidCardRarityException extends RuntimeException {
    public InvalidCardRarityException(String invalidRarity) {
        super("Raridade inválida: " + invalidRarity);
    }
}
