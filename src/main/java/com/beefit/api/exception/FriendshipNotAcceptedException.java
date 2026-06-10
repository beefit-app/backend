package com.beefit.api.exception;

public class FriendshipNotAcceptedException extends RuntimeException{
    public FriendshipNotAcceptedException(){
        super("Não é possível deletar uma amizade ainda não aceita.");
    }
}
