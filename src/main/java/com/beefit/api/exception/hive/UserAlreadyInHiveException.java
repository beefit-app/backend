package com.beefit.api.exception.hive;

public class UserAlreadyInHiveException extends RuntimeException {
    public UserAlreadyInHiveException() {
        super("Esse usuário já está nessa colmeia.");
    }
}
