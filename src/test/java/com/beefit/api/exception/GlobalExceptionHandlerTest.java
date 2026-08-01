package com.beefit.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.beefit.api.exception.user.EmailAlreadyInUse;
import com.beefit.api.exception.security.RateLimitExceededException;
import com.beefit.api.exception.user.InvalidPasswordException;
import com.beefit.api.exception.workout.InvalidMuscleGroupException;
import com.beefit.api.exception.workout.WorkoutSessionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpHeaders;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRegisterConflictExceptionsReturnsConflictPayload() {
        MockHttpServletRequest request = request("/auth/register");

        ErrorResponse response = handler.handleRegisterConflictExceptions(
                new EmailAlreadyInUse("email em uso"),
                request);

        assertEquals(409, response.status());
        assertEquals("Dados inválidos", response.error());
        assertEquals("email em uso", response.message());
        assertEquals("/auth/register", response.path());
    }

    @Test
    void handlePasswordExceptionsReturnsBadRequestPayload() {
        MockHttpServletRequest request = request("/api/profile/me/password");

        ErrorResponse response = handler.handleBadRequestRules(
                new InvalidPasswordException("Senha atual incorreta."),
                request);

        assertEquals(400, response.status());
        assertEquals("Business Rule Violation", response.error());
        assertEquals("Senha atual incorreta.", response.message());
    }

    @Test
    void handleWorkoutElementsNotFoundExceptionReturnsNotFoundPayload() {
        MockHttpServletRequest request = request("/api/workout/123/finish");

        ErrorResponse response = handler.handleWorkoutElementsNotFoundException(
                new WorkoutSessionNotFoundException(),
                request);

        assertEquals(404, response.status());
        assertEquals("Elementos do treino não encontrados.", response.error());
        assertEquals("/api/workout/123/finish", response.path());
    }

    @Test
    void handleInvalidMuscleGroupExceptionReturnsBadRequestPayload() {
        MockHttpServletRequest request = request("/api/workout/123/muscle-groups/foo");

        ErrorResponse response = handler.handleBadRequestRules(
                new InvalidMuscleGroupException("foo"),
                request);

        assertEquals(400, response.status());
        assertEquals(List.of(), response.fields());
        assertEquals("/api/workout/123/muscle-groups/foo", response.path());
    }

    @Test
    void handleRateLimitExceededReturnsRetryAfterHeader() {
        var response = handler.handleRateLimitExceeded(
                new RateLimitExceededException(42),
                request("/auth/login"));

        assertEquals(429, response.getStatusCode().value());
        assertEquals("42", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("Limite de requisições excedido", response.getBody().error());
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
