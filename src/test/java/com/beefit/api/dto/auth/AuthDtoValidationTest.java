package com.beefit.api.dto.auth;

import com.beefit.api.dto.user.ChangePasswordDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthDtoValidationTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void acceptsRegistrationWithinThePublicContract() {
        var dto = new RegisterDTO(
                "Theo Silva",
                "theo",
                "theo@beefit.com",
                "senha123",
                80F,
                4,
                "device-1");

        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void rejectsShortOrOversizedRegistrationFields() {
        var dto = new RegisterDTO(
                "Ana",
                "u".repeat(256),
                "e".repeat(246) + "@email.com",
                "12345",
                80F,
                4,
                "d".repeat(256));

        Set<ConstraintViolation<RegisterDTO>> violations = validator.validate(dto);

        assertHasViolationFor(violations, "fullName");
        assertHasViolationFor(violations, "username");
        assertHasViolationFor(violations, "email");
        assertHasViolationFor(violations, "password");
        assertHasViolationFor(violations, "deviceId");
    }

    @Test
    void rejectsRegistrationPasswordWithoutLettersAndNumbers() {
        var dto = new RegisterDTO(
                "Theo Silva",
                "theo",
                "theo@beefit.com",
                "somenteletras",
                80F,
                4,
                "device-1");

        assertHasViolationFor(validator.validate(dto), "password");
    }

    @Test
    void appliesTheSamePolicyWhenChangingPassword() {
        assertTrue(validator.validate(new ChangePasswordDTO("atual", "novaSenha123")).isEmpty());
        assertTrue(validator.validate(new ChangePasswordDTO("atual", "açúcar123")).isEmpty());
        assertHasViolationFor(
                validator.validate(new ChangePasswordDTO("atual", "somenteletras")),
                "newPassword");
    }

    @Test
    void rejectsOversizedAuthenticationPayloads() {
        var authentication = new AuthenticationDTO(
                "e".repeat(246) + "@email.com",
                "p".repeat(257),
                "d".repeat(256));
        var refresh = new RefreshTokenRequestDTO("r".repeat(513), "d".repeat(256));
        var logout = new LogoutRequestDTO("r".repeat(513), "d".repeat(256));

        assertFalse(validator.validate(authentication).isEmpty());
        assertFalse(validator.validate(refresh).isEmpty());
        assertFalse(validator.validate(logout).isEmpty());
    }

    private static <T> void assertHasViolationFor(Set<ConstraintViolation<T>> violations, String field) {
        assertTrue(
                violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().equals(field)),
                () -> "Esperava uma violação para o campo " + field);
    }
}
