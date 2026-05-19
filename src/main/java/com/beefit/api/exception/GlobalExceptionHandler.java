package com.beefit.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Essa classe serve para montar uma response personalizada para cada exception na aplicação
// Assim que chegar na exception, o fluxo será interrompido e a response será encaminhada para o cliente

@RestControllerAdvice // Anotação específica para essa classe, justamente para ela conseguir interceptar as exceptions
public class GlobalExceptionHandler {

    @ExceptionHandler({EmailAlreadyInUse.class, UsernameAlreadyInUse.class}) // Essa anotação indica o tipo de exception que deve ser interceptada
    public ResponseEntity<ErrorResponse> handleRegisterConflictExceptions(RuntimeException e) {

        ErrorResponse errorResponse = new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Dados Inválidos", e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
