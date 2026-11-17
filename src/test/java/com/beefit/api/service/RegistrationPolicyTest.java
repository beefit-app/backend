package com.beefit.api.service;

import com.beefit.api.exception.user.PermissionException;
import com.beefit.api.exception.user.RegistrationClosedException;
import com.beefit.api.exception.user.RegistrationRestrictedException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistrationPolicyTest {

    private RegistrationPolicy policy(boolean registrationEnabled, boolean betaEnabled, String... allowedEmails) {
        RegistrationPolicy policy = new RegistrationPolicy();
        ReflectionTestUtils.setField(policy, "registrationEnabled", registrationEnabled);
        ReflectionTestUtils.setField(policy, "betaEnabled", betaEnabled);
        ReflectionTestUtils.setField(policy, "allowedEmails", allowedEmails);
        return policy;
    }

    @Test
    void closedRegistrationThrowsPermissionSubtype() {
        var ex = assertThrows(RegistrationClosedException.class,
                () -> policy(false, false).ensureRegistrationOpen());
        // Mantém o contrato HTTP de /auth/register (handler de PermissionException).
        assertInstanceOf(PermissionException.class, ex);
    }

    @Test
    void openRegistrationPasses() {
        assertDoesNotThrow(() -> policy(true, false).ensureRegistrationOpen());
    }

    @Test
    void betaDisabledAcceptsAnyEmail() {
        assertDoesNotThrow(() -> policy(true, false).ensureEmailAllowed("qualquer@example.com"));
    }

    @Test
    void betaComparesIgnoringCaseAndSurroundingSpaces() {
        RegistrationPolicy policy = policy(true, true, " Convidado@Example.com ");
        assertDoesNotThrow(() -> policy.ensureEmailAllowed("convidado@example.com"));
        assertDoesNotThrow(() -> policy.ensureEmailAllowed("  CONVIDADO@EXAMPLE.COM"));
    }

    @Test
    void betaRejectsEmailOutsideAllowlistOrMissing() {
        RegistrationPolicy policy = policy(true, true, "convidado@example.com", "");
        assertThrows(RegistrationRestrictedException.class, () -> policy.ensureEmailAllowed("outro@example.com"));
        assertThrows(RegistrationRestrictedException.class, () -> policy.ensureEmailAllowed(null));
        assertThrows(RegistrationRestrictedException.class, () -> policy(true, true).ensureEmailAllowed(""));
    }
}
