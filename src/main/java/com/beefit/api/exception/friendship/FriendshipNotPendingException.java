package com.beefit.api.exception.friendship;

public class FriendshipNotPendingException extends RuntimeException{

    public FriendshipNotPendingException(){
        super("Não foi enviada solicitação de amizade.");
    }
}
