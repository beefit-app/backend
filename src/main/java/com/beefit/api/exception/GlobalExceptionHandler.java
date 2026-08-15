package com.beefit.api.exception;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.beefit.api.exception.exercises.CatalogExerciseNotFound;
import com.beefit.api.exception.exercises.DatabaseSeederFailException;
import com.beefit.api.exception.friendship.*;
import com.beefit.api.exception.hive.ExceededHiveLimitException;
import com.beefit.api.exception.hive.HiveNotFoundException;
import com.beefit.api.exception.hive.InvalidTimePeriodException;
import com.beefit.api.exception.hive.UserAlreadyInHiveException;
import com.beefit.api.exception.media.MediaNotFoundException;
import com.beefit.api.exception.security.RateLimitExceededException;
import com.beefit.api.exception.user.*;
import com.beefit.api.exception.workout.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
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
        // O conflito sempre nasce de um campo especifico do cadastro. Sem atribui-lo, a
        // tela so consegue mostrar um aviso solto e o input responsavel fica sem marcacao.
        String field = e instanceof EmailAlreadyInUse ? "email" : "username";

        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Dados inválidos",
                e.getMessage(),
                List.of(new ErrorCampo(field, e.getMessage())),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(InvalidSessionException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidSessionException(InvalidSessionException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );
    }

    // Sem estes dois tratadores, a excecao lancada pelo AuthenticationManager escapa
    // do DispatcherServlet, o ExceptionTranslationFilter do Spring Security a captura
    // e responde pelo authenticationEntryPoint — cujo caso padrao e "Autenticação
    // obrigatória.". Aquele ponto de entrada e para quem acessa rota protegida sem
    // token, nao para login recusado. Estar no @RestControllerAdvice faz o resolvedor
    // do DispatcherServlet atender antes de a excecao chegar ao filtro.

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(BadCredentialsException e, HttpServletRequest request) {
        // Uma frase so para senha errada e e-mail inexistente: o DaoAuthenticationProvider
        // ja esconde o "usuario nao encontrado", e distinguir os dois permitiria
        // descobrir quais e-mails existem no sistema.
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "INVALID_CREDENTIALS",
                "E-mail ou senha inválidos.",
                List.of(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler({ DisabledException.class, LockedException.class })
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleBlockedAccount(RuntimeException e, HttpServletRequest request) {
        // User.isEnabled() devolve !isBanned(), entao quem esta banido lanca
        // DisabledException, nao BadCredentialsException. Responder "senha invalida"
        // aqui faria a pessoa tentar de novo ate esbarrar no limite de 5 tentativas
        // por conta a cada 15 minutos. Codigo e texto iguais aos do
        // authenticationEntryPoint, que trata o mesmo caso no outro caminho.
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "ACCOUNT_BANNED",
                "Esta conta está temporariamente bloqueada.",
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableMessage(HttpMessageNotReadableException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Erro de desserialização",
                "O corpo da requisição está inválido ou mal formatado.",
                List.of(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler({ WorkoutSessionNotFoundException.class, WorkoutTemplateNotFoundException.class,
            CatalogExerciseNotFound.class, PostNotFoundException.class, MediaNotFoundException.class })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleWorkoutElementsNotFoundException(RuntimeException e,
            HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Elementos do treino não encontrados.",
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
                "Estado da sessão de treino inválido.",
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

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIdempotencyConflict(IdempotencyConflictException e, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.CONFLICT.value(), "Conflito de idempotência",
                e.getMessage(), List.of(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Requisição inválida",
                e.getMessage(), List.of(), request.getRequestURI());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Limite de requisições excedido",
                e.getMessage(),
                List.of(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(response);
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
    @ExceptionHandler({HiveNotFoundException.class, UserNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
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
    @ResponseStatus(HttpStatus.CONFLICT)
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

    @ExceptionHandler(InventoryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFoundInventory(RuntimeException e, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Erro ao buscar inventário",
                e.getMessage(),
                List.of(),
                request.getRequestURI()
        );
    }
}
