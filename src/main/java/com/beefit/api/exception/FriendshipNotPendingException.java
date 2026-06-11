package com.beefit.api.exception;

public class FriendshipNotPendingException extends RuntimeException{

    public FriendshipNotPendingException(){
        super("Não foi enviada solicitação de amizade.");
    }
}
