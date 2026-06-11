package com.beefit.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
        assertEquals("Elementos do treino não encontrado.", response.error());
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

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
