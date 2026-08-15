package com.beefit.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.beefit.api.exception.user.EmailAlreadyInUse;
import com.beefit.api.exception.security.RateLimitExceededException;
import com.beefit.api.exception.user.InvalidPasswordException;
import com.beefit.api.exception.hive.HiveNotFoundException;
import com.beefit.api.exception.hive.UserAlreadyInHiveException;
import com.beefit.api.exception.workout.InventoryNotFoundException;
import com.beefit.api.exception.workout.InvalidMuscleGroupException;
import com.beefit.api.exception.workout.WorkoutSessionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

    @Test
    void handleBadCredentialsExplicaOQueAconteceu() {
        // Sem este tratador a excecao escapava para o authenticationEntryPoint do
        // Spring Security e o usuario lia "Autenticação obrigatória." ao errar a senha.
        ErrorResponse response = handler.handleBadCredentials(
                new BadCredentialsException("Bad credentials"),
                request("/auth/login"));

        assertEquals(401, response.status());
        assertEquals("INVALID_CREDENTIALS", response.error());
        assertEquals("E-mail ou senha inválidos.", response.message());
        assertEquals("/auth/login", response.path());
    }

    @Test
    void handleBadCredentialsNaoRevelaQualCampoFalhou() {
        // E-mail inexistente e senha errada precisam produzir a mesma resposta:
        // distinguir permitiria descobrir quais e-mails existem no sistema.
        ErrorResponse senhaErrada = handler.handleBadCredentials(
                new BadCredentialsException("Bad credentials"), request("/auth/login"));
        ErrorResponse usuarioInexistente = handler.handleBadCredentials(
                new BadCredentialsException("User not found"), request("/auth/login"));

        assertEquals(senhaErrada.message(), usuarioInexistente.message());
        assertEquals(senhaErrada.status(), usuarioInexistente.status());
    }

    @Test
    void handleBlockedAccountNaoMandaTentarSenhaDeNovo() {
        // User.isEnabled() devolve !isBanned(), entao conta banida lanca
        // DisabledException. Responder "senha invalida" faria a pessoa insistir ate
        // esbarrar no limite de tentativas.
        ErrorResponse response = handler.handleBlockedAccount(
                new DisabledException("conta banida"),
                request("/auth/login"));

        assertEquals(403, response.status());
        assertEquals("ACCOUNT_BANNED", response.error());
        assertEquals("Esta conta está temporariamente bloqueada.", response.message());
    }

    @Test
    void hiveNotFoundUsesHttpNotFoundStatus() throws Exception {
        exceptionStatusClient().perform(get("/test-errors/hive-not-found"))
                .andExpect(status().isNotFound());
    }

    @Test
    void userAlreadyInHiveUsesHttpConflictStatus() throws Exception {
        exceptionStatusClient().perform(get("/test-errors/user-already-in-hive"))
                .andExpect(status().isConflict());
    }

    @Test
    void inventoryNotFoundUsesHttpNotFoundStatus() throws Exception {
        exceptionStatusClient().perform(get("/test-errors/inventory-not-found"))
                .andExpect(status().isNotFound());
    }

    private MockMvc exceptionStatusClient() {
        return MockMvcBuilders.standaloneSetup(new ExceptionStatusController())
                .setControllerAdvice(handler)
                .build();
    }

    @RestController
    private static class ExceptionStatusController {

        @GetMapping("/test-errors/hive-not-found")
        void hiveNotFound() {
            throw new HiveNotFoundException();
        }

        @GetMapping("/test-errors/user-already-in-hive")
        void userAlreadyInHive() {
            throw new UserAlreadyInHiveException();
        }

        @GetMapping("/test-errors/inventory-not-found")
        void inventoryNotFound() {
            throw new InventoryNotFoundException();
        }
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
