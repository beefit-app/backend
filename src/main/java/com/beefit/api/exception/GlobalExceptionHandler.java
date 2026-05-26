package com.beefit.api.exception;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

// Essa classe serve para montar uma response personalizada para cada exception na aplicação
// Assim que chegar na exception, o fluxo será interrompido e a response será encaminhada para o cliente

@RestControllerAdvice // Anotação específica para essa classe, justamente para ela conseguir
                      // interceptar as exceptions
public class GlobalExceptionHandler {

    @ExceptionHandler({ EmailAlreadyInUse.class, UsernameAlreadyInUse.class }) // Essa anotação indica o tipo de
                                                                               // exception que deve ser interceptada
    // @ResponseStatus
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleRegisterConflictExceptions(RuntimeException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Dados inválidos",
                e.getMessage(),
                List.of(),
                request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class) // Essa anotação indica
                                                             // o tipo de exception
                                                             // que deve ser
                                                             // interceptada
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationExceptions(MethodArgumentNotValidException e, HttpServletRequest request) {

        List<ErrorCampo> fields = new ArrayList<>();

        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.add(new ErrorCampo(error.getField(), error.getDefaultMessage()));
        }

        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                "Campos enviados inválidos",
                fields,
                request.getRequestURI());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePasswordExceptions(InvalidPasswordException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Dados inválidos",
                e.getMessage(),
                List.of(),
                request.getRequestURI());
    }

    @ExceptionHandler({ WorkoutSessionNotFoundException.class, WorkoutTemplateNotFoundException.class,
            CatalogExerciseNotFound.class })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleWorkoutElementsNotFoundException(RuntimeException e,
            HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Elementos do treino não encontrado.",
                e.getMessage(),
                List.of(),
                request.getRequestURI());
    }

    @ExceptionHandler(InvalidWorkoutSessionStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidStateExceptions(InvalidWorkoutSessionStateException e,
            HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Estado da sessão de treino inválido",
                e.getMessage(),
                List.of(),
                request.getRequestURI());
    }

    @ExceptionHandler(WorkoutAlreadyInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleWorkoutAlreadyInProgressException(WorkoutAlreadyInProgressException e,
            HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "O usuário já possui um treino ativo",
                e.getMessage(),
                List.of(),
                request.getRequestURI());

    }

    @ExceptionHandler(InvalidMuscleGroupException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvaidMuscleGroupException(InvalidMuscleGroupException e, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                null,
                e.getMessage(),
                List.of(),
                request.getRequestURI());

    }
}
