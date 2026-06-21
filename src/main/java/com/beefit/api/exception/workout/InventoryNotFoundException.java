package com.beefit.api.exception.workout;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException() {
        super("Inventário não encontrado.");
    }
}
