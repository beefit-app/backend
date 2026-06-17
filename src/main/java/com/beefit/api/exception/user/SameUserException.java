package com.beefit.api.exception.user;

public class SameUserException extends RuntimeException{

    public SameUserException(){
        super("Você não pode adicionar a si mesmo.");
    }
}
