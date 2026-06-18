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

    @ExceptionHandler({ EmailAlreadyInUse.class, UsernameAlreadyInUse.class }) // Essa anotação indica o tipo de exception que deve ser interceptada
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleRegisterConflictExceptions(RuntimeException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Dados inválidos",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
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
                request.getRequestURI()
        );
    }

    @ExceptionHandler({ WorkoutSessionNotFoundException.class, WorkoutTemplateNotFoundException.class,
            CatalogExerciseNotFound.class, PostNotFoundException.class })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleWorkoutElementsNotFoundException(RuntimeException e,
            HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Elementos do treino não encontrado.",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(InvalidWorkoutSessionStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidStateExceptions(InvalidWorkoutSessionStateException e,
            HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Estado da sessão de treino inválido",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(InvalidWorkoutSnapshotException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidWorkoutSnapshotException(InvalidWorkoutSnapshotException e,
            HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Snapshot do treino inválido",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler({SameUserException.class, FriendshipAlreadyExistException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSendRequestException(RuntimeException e,
                                                               HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Solicitação de amizade inválida",
                e.getMessage(),
                List.of(),
                request.getRequestURI());
    }

    @ExceptionHandler({FriendshipNotAcceptedException.class,
            FriendshipNotPendingException.class,
            FriendshipNotFound.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleFriendshipException(RuntimeException e,
                                                 HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Não é possível essa ação para a solicitação de amizade",
                e.getMessage(),
                List.of(),
                request.getRequestURI());
    }

    @ExceptionHandler(DifferentUserException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDifferentUserException(DifferentUserException e,
                                                 HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Erro ao responder solicitação",
                e.getMessage(),
                List.of(),
                request.getRequestURI());
    }

    @ExceptionHandler(WorkoutAlreadyInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleWorkoutAlreadyInProgressException(WorkoutAlreadyInProgressException e,
            HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "O usuário já possui um treino ativo",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );

    }


    @ExceptionHandler(DatabaseSeederFailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDatabaseSeederFailException(DatabaseSeederFailException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                e.getCause().getMessage(),
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );

    }

    // Abaixo estão algumas funções "genéricas" para agrupar exceções de mesmo HttpStatus

    // Trata erros de recurso não encontrado (Status 404)
    @ExceptionHandler({HiveNotFoundException.class})
    public ErrorResponse handleNotFound(RuntimeException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Resource Not Found",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );
    }

    // Trata erros de conflito (Status 409)
    @ExceptionHandler({UserAlreadyInHiveException.class})
    public ErrorResponse handleUserAlreadyInHiveException(RuntimeException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );

    }

    // Trata erros de regras de negocio violadas (Status 400)
    @ExceptionHandler({
            InvalidPasswordException.class,
            InvalidCardRarityException.class,
            InvalidMuscleGroupException.class,
            InvalidTimePeriodException.class,
            ExceededHiveLimitException.class,
            PermissionException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequestRules(RuntimeException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Business Rule Violation",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );
    }
}
