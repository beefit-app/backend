package com.beefit.api.exception;

public class SameUserException extends RuntimeException{

    public SameUserException(){
        super("Você não pode adicionar a si mesmo.");
    }
}
