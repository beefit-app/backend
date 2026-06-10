package com.beefit.api.exception;

public class FriendshipAlreadyExistException extends RuntimeException{
    public FriendshipAlreadyExistException(){
        super("Não é possível enviar uma nova solicitação.");
    }
}
